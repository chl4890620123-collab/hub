package com.hub.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.util.HttpRequestFactories;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class GoogleDriveConnector implements ReadOnlyConnector {
    private static final String GOOGLE_DOC = "application/vnd.google-apps.document";
    private static final String GOOGLE_SHEET = "application/vnd.google-apps.spreadsheet";
    private static final String GOOGLE_SLIDE = "application/vnd.google-apps.presentation";
    private static final String GOOGLE_FOLDER = "application/vnd.google-apps.folder";
    private static final Pattern DRIVE_ID = Pattern.compile("[A-Za-z0-9_-]{5,200}");
    private static final int MAX_PAGES = 5;
    // 100/page - caps a runaway account at 1000 folders rather than looping forever.
    private static final int MAX_TARGET_PAGES = 10;

    private final RestClient client;
    private final ObjectMapper json;

    public GoogleDriveConnector(RestClient.Builder builder, ObjectMapper json) {
        this.client = builder
                .baseUrl("https://www.googleapis.com")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(90)))
                .build();
        this.json = json;
    }

    @Override
    public String type() {
        return "GOOGLE_DRIVE";
    }

    /**
     * Scope is a Drive folder id. This adapter is intentionally read-only: Hub downloads/exports
     * snapshots and never modifies or deletes the source Drive file.
     */
    @Override
    public List<ExternalContent> fetch(String folderId, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Google Drive 계정을 먼저 연결해 주세요.");
        String safeFolder = normalizeFolderId(folderId);
        if (!DRIVE_ID.matcher(safeFolder).matches()) {
            throw new IllegalArgumentException("Google Drive 폴더 ID 또는 폴더 주소를 확인해 주세요.");
        }

        String rootName = folderName(safeFolder, token);
        List<ExternalContent> out = new ArrayList<>();
        Deque<FolderScope> pending = new ArrayDeque<>();
        pending.add(new FolderScope(safeFolder, rootName, "Google Drive / " + rootName, 0));
        int visitedFolders = 0;

        // Recursive enough for an MVP team drive, but bounded to avoid accidental huge imports.
        while (!pending.isEmpty() && visitedFolders < 25) {
            FolderScope folder = pending.removeFirst();
            visitedFolders++;
            String nextPageToken = null;
            for (int page = 0; page < MAX_PAGES; page++) {
                JsonNode response = listFiles(folder.id(), nextPageToken, token);
                JsonNode files = response.path("files");
                if (files.isArray()) {
                    for (JsonNode file : files) {
                        String id = file.path("id").asText();
                        String name = file.path("name").asText("Drive file");
                        String mime = file.path("mimeType").asText();
                        if (GOOGLE_FOLDER.equals(mime)) {
                            if (folder.depth() < 3) {
                                pending.addLast(new FolderScope(id, name, folder.path() + " / " + name, folder.depth() + 1));
                            }
                            continue;
                        }

                        Downloaded downloaded = download(id, name, mime, token);
                        if (downloaded == null) continue;
                        out.add(new ExternalContent(
                                id,
                                "DRIVE_FILE",
                                downloaded.filename(),
                                downloaded.text(),
                                downloaded.bytes(),
                                downloaded.contentType(),
                                "",
                                file.path("webViewLink").asText(""),
                                ConnectorSupport.date(file.path("modifiedTime").asText("")),
                                Map.of(
                                        "mimeType", mime,
                                        "driveName", name,
                                        "folderId", folder.id(),
                                        "folderName", folder.name(),
                                        "location", folder.path() + " / " + name
                                )
                        ));
                    }
                }
                nextPageToken = response.path("nextPageToken").asText("");
                if (nextPageToken.isBlank()) break;
            }
        }
        return out;
    }

    private String folderName(String folderId, String token) {
        try {
            JsonNode folder = getJson("/drive/v3/files/" + folderId + "?fields=id,name,webViewLink", token);
            String name = folder.path("name").asText("").trim();
            return name.isBlank() ? folderId : name;
        } catch (RuntimeException ignored) {
            return folderId;
        }
    }

    private JsonNode listFiles(String folderId, String pageToken, String token) {
        String q = "'" + folderId + "' in parents and trashed=false";
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/drive/v3/files")
                .queryParam("q", q)
                .queryParam("pageSize", 50)
                .queryParam("fields", "nextPageToken,files(id,name,mimeType,modifiedTime,webViewLink)");
        if (pageToken != null && !pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
        // RestClient encodes the URI it is given, so encoding here too turned every %27 into %2527 and
        // Drive rejected the query: the folder listing failed for every import.
        return getJson(builder.build().toUriString(), token);
    }

    private Downloaded download(String id, String name, String mime, String token) {
        if (!DRIVE_ID.matcher(id).matches()) throw new IllegalStateException("Drive returned an invalid file id");
        if (GOOGLE_DOC.equals(mime)) {
            byte[] bytes = getBytes("/drive/v3/files/" + id + "/export?mimeType=text/plain", token);
            return new Downloaded(
                    ensureExtension(name, ".txt"),
                    "text/plain",
                    bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8),
                    null
            );
        }
        if (GOOGLE_SHEET.equals(mime)) {
            byte[] bytes = getBytes(
                    "/drive/v3/files/" + id + "/export?mimeType=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    token
            );
            return new Downloaded(
                    ensureExtension(name, ".xlsx"),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    null,
                    bytes
            );
        }
        if (GOOGLE_SLIDE.equals(mime)) {
            byte[] bytes = getBytes(
                    "/drive/v3/files/" + id + "/export?mimeType=application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    token
            );
            return new Downloaded(
                    ensureExtension(name, ".pptx"),
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    null,
                    bytes
            );
        }
        if (mime.startsWith("text/")) {
            byte[] bytes = getBytes("/drive/v3/files/" + id + "?alt=media", token);
            return new Downloaded(
                    name,
                    mime,
                    bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8),
                    null
            );
        }

        // PDF, DOCX, images and other normal Drive files are imported as binary snapshots.
        byte[] bytes = getBytes("/drive/v3/files/" + id + "?alt=media", token);
        return bytes == null || bytes.length == 0 ? null : new Downloaded(name, mime, null, bytes);
    }

    private byte[] getBytes(String uri, String token) {
        try {
            return client.get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Google Drive download failed", e);
        }
    }

    private JsonNode getJson(String uri, String token) {
        try {
            String body = client.get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(String.class);
            return ConnectorSupport.json(json, body, "Invalid Google Drive response");
        } catch (RestClientException e) {
            throw new IllegalStateException("Google Drive API request failed", e);
        }
    }


    private static String ensureExtension(String name, String extension) {
        return name.toLowerCase().endsWith(extension) ? name : name + extension;
    }

    private static String normalizeFolderId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return "";
        if (DRIVE_ID.matcher(value).matches()) return value;
        try {
            String path = URI.create(value).getPath();
            if (path == null) return value;
            String marker = "/folders/";
            int start = path.indexOf(marker);
            if (start < 0) return value;
            String candidate = path.substring(start + marker.length()).split("/", 2)[0];
            return candidate.trim();
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private record FolderScope(String id, String name, String path, int depth) {}
    private record Downloaded(String filename, String contentType, String text, byte[] bytes) {}

    /** Folders the connected Google account can read. Follows Drive's nextPageToken instead of
     * stopping at the first page, or an account with >100 folders silently lost everything past it. */
    @Override
    public List<ConnectorTarget> targets(String token) {
        List<ConnectorTarget> out = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < MAX_TARGET_PAGES; page++) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/drive/v3/files")
                    .queryParam("q", "mimeType='application/vnd.google-apps.folder' and trashed=false")
                    .queryParam("fields", "nextPageToken,files(id,name)")
                    .queryParam("pageSize", 100)
                    .queryParam("orderBy", "name");
            if (pageToken != null && !pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
            JsonNode body = getJson(builder.build().toUriString(), token);
            JsonNode files = body.path("files");
            if (files.isArray()) {
                for (JsonNode folder : files) {
                    String id = folder.path("id").asText("");
                    if (id.isBlank()) continue;
                    out.add(new ConnectorTarget(id, folder.path("name").asText("이름 없음"), "Drive 폴더"));
                }
            }
            pageToken = body.path("nextPageToken").asText("");
            if (pageToken.isBlank()) break;
        }
        return out;
    }
}
