package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.service.export.ExportDocumentLoader;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Write-side Google Drive integration kept separate from the read-only connector (GoogleDriveConnector
 * never modifies or deletes anything). Creates a brand new file in a folder the account can already
 * see - there is no "propose a change" concept in Drive the way GitHub has pull requests.
 */
@Service
public class GoogleDriveExportService {
    private static final Pattern DRIVE_ID = Pattern.compile("[A-Za-z0-9_-]{5,200}");

    private final RestClient client;
    private final ObjectMapper json;
    private final GoogleAccessTokenProvider tokens;
    private final ExportDocumentLoader loader;

    public GoogleDriveExportService(RestClient.Builder builder, ObjectMapper json,
                                    GoogleAccessTokenProvider tokens, ExportDocumentLoader loader) {
        this.client = builder
                .baseUrl("https://www.googleapis.com")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .build();
        this.json = json;
        this.tokens = tokens;
        this.loader = loader;
    }

    public record ExportRequest(String folderId, String filename) {}
    public record ExportResult(String status, String fileId, String webViewLink) {}

    public ExportResult export(long projectId, long documentId, long userId, ExportRequest request) {
        if (request == null) throw new IllegalArgumentException("Google Drive 저장 요청이 비어 있습니다.");
        String folderId = requiredFolderId(request.folderId());
        var loaded = loader.load(projectId, documentId);
        String filename = safeFilename(request.filename(), loaded.meta().originalName());

        String token = tokens.accessToken(userId);

        String boundary = "hub-export-" + UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", filename);
        metadata.put("parents", List.of(folderId));
        String body;
        try {
            body = "--" + boundary + "\r\n"
                    + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                    + json.writeValueAsString(metadata) + "\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                    + loaded.content() + "\r\n"
                    + "--" + boundary + "--";
        } catch (Exception e) {
            throw new IllegalStateException("Google Drive 저장 요청을 만들지 못했습니다.", e);
        }

        JsonNode saved = upload(body, boundary, token);
        return new ExportResult("SAVED", saved.path("id").asText(""), blankToNull(saved.path("webViewLink").asText("")));
    }

    private JsonNode upload(String multipartBody, String boundary, String token) {
        try {
            String response = client.post()
                    .uri("/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary))
                    .body(multipartBody.getBytes(StandardCharsets.UTF_8))
                    .retrieve().body(String.class);
            return json.readTree(response == null ? "{}" : response);
        } catch (HttpClientErrorException e) {
            throw driveError(e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Google Drive에 파일을 저장하지 못했습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Google Drive 응답을 처리하지 못했습니다.", e);
        }
    }

    private static IllegalStateException driveError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        String message = switch (status) {
            case 401 -> "Google Drive 연결이 만료되었습니다. 계정을 다시 연결해 주세요.";
            // drive.file scope only lets the app see files/folders it created or that were explicitly
            // opened with it - a folder picked from an older read-only-scoped connection lands here.
            case 403 -> "이 Google Drive 폴더에 쓰기 권한이 없습니다. 계정을 다시 연결한 뒤 시도해 주세요.";
            case 404 -> "Google Drive 폴더를 찾을 수 없습니다. 목록에서 다시 선택해 주세요.";
            case 429 -> "Google Drive 요청이 많아 잠시 제한되었습니다. 잠시 후 다시 시도해 주세요.";
            default -> "Google Drive 요청이 실패했습니다. (HTTP " + status + ")";
        };
        return new IllegalStateException(message, e);
    }

    private static String requiredFolderId(String value) {
        String folderId = value == null ? "" : value.trim();
        if (!DRIVE_ID.matcher(folderId).matches()) {
            throw new IllegalArgumentException("Google Drive 폴더를 목록에서 다시 선택해 주세요.");
        }
        return folderId;
    }

    private static String safeFilename(String requested, String fallback) {
        String name = (requested == null || requested.isBlank() ? fallback : requested).trim();
        name = name.replace('\\', '_').replace('/', '_');
        if (name.isBlank() || name.length() > 300) {
            throw new IllegalArgumentException("저장할 파일 이름을 다시 확인해 주세요.");
        }
        return name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
