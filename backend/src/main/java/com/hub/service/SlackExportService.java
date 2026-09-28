package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.service.export.ExportDocumentLoader;
import com.hub.util.HttpRequestFactories;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Write-side Slack integration kept separate from the read-only connector (SlackConnector only ever
 * reads message history). Uploads the document as a real Slack file into a channel - Slack's own
 * "external upload" flow: reserve an upload URL, PUT the bytes to it, then attach the uploaded file
 * to the channel. Requires the chat:write and files:write scopes in addition to the read scopes
 * already granted for import (see ExternalOAuthService).
 */
@Service
public class SlackExportService {
    private static final Pattern CHANNEL_ID = Pattern.compile("[CDG][A-Z0-9]{8,}");

    private final RestClient client;
    // A separate, base-URL-less client: Slack's upload_url is a one-time external address, not under
    // https://slack.com/api, and needs no Slack bearer token (the URL itself is pre-signed).
    private final RestClient uploadClient = RestClient.create();
    private final ObjectMapper json;
    private final ExternalOAuthTokenStore tokens;
    private final ExportDocumentLoader loader;

    public SlackExportService(RestClient.Builder builder, ObjectMapper json,
                              ExternalOAuthTokenStore tokens, ExportDocumentLoader loader) {
        this.client = builder
                .baseUrl("https://slack.com/api")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .build();
        this.json = json;
        this.tokens = tokens;
        this.loader = loader;
    }

    public record ExportRequest(String channelId, String message) {}
    public record ExportResult(String status, String fileId, String permalink) {}

    public ExportResult export(long projectId, long documentId, long userId, ExportRequest request) {
        if (request == null) throw new IllegalArgumentException("Slack 전송 요청이 비어 있습니다.");
        String channelId = requiredChannel(request.channelId());
        String token = tokens.get(userId, "SLACK");
        if (token == null || token.isBlank()) throw new IllegalStateException("Slack 계정을 먼저 연결해 주세요.");

        var loaded = loader.load(projectId, documentId);
        byte[] bytes = loaded.content().getBytes(StandardCharsets.UTF_8);
        String filename = ensureExtension(loaded.meta().originalName());

        JsonNode reserved = get("/files.getUploadURLExternal?filename=" + encode(filename) + "&length=" + bytes.length, token);
        String uploadUrl = reserved.path("upload_url").asText("");
        String fileId = reserved.path("file_id").asText("");
        if (uploadUrl.isBlank() || fileId.isBlank()) throw new IllegalStateException("Slack 업로드 준비에 실패했습니다.");

        putBytes(uploadUrl, filename, bytes);

        String comment = blank(request.message())
                ? "Hub에서 보낸 문서: " + loaded.meta().originalName()
                : request.message().trim();
        Map<String, Object> complete = new LinkedHashMap<>();
        complete.put("channel_id", channelId);
        complete.put("initial_comment", comment);
        complete.put("files", List.of(Map.of("id", fileId, "title", filename)));
        JsonNode completed = post("/files.completeUploadExternal", complete, token);
        JsonNode files = completed.path("files");
        JsonNode file = files.isArray() && !files.isEmpty() ? files.get(0) : completed;
        return new ExportResult("UPLOADED", file.path("id").asText(fileId), blankToNull(file.path("permalink").asText("")));
    }

    private void putBytes(String uploadUrl, String filename, byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.TEXT_PLAIN);
        body.add("file", new HttpEntity<>(resource, partHeaders));
        try {
            uploadClient.post()
                    .uri(URI.create(uploadUrl))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IllegalStateException("Slack에 파일을 업로드하지 못했습니다.", e);
        }
    }

    private JsonNode get(String path, String token) {
        return call(() -> client.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().body(String.class));
    }

    private JsonNode post(String path, Object payload, String token) {
        return call(() -> {
            try {
                return client.post().uri(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json.writeValueAsString(payload))
                        .retrieve().body(String.class);
            } catch (RestClientException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Slack 요청을 만들지 못했습니다.", e);
            }
        });
    }

    private JsonNode call(java.util.function.Supplier<String> request) {
        try {
            String body = request.get();
            JsonNode node = json.readTree(body == null ? "{}" : body);
            if (!node.path("ok").asBoolean(false)) {
                throw new IllegalStateException(slackError(node.path("error").asText("unknown_error")));
            }
            return node;
        } catch (RestClientException e) {
            throw new IllegalStateException("Slack에 연결하지 못했습니다.", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Slack 응답을 처리하지 못했습니다.", e);
        }
    }

    private static String slackError(String code) {
        return switch (code) {
            case "not_in_channel" -> "이 채널에 Hub 앱을 먼저 초대해 주세요. 채널에서 /invite @Hub 를 실행하면 됩니다.";
            case "channel_not_found" -> "채널을 찾을 수 없습니다. 목록을 다시 불러온 뒤 선택해 주세요.";
            case "missing_scope", "not_allowed_token_type" -> "Slack 토큰 권한이 부족합니다. Slack 계정을 다시 연결해 chat:write, files:write 권한을 추가해 주세요.";
            case "invalid_auth", "token_revoked", "account_inactive" -> "Slack 토큰이 유효하지 않습니다. 다시 연결해 주세요.";
            case "ratelimited" -> "Slack 요청이 많아 잠시 제한되었습니다. 잠시 후 다시 시도해 주세요.";
            default -> "Slack에 문서를 보내지 못했습니다 (" + code + ")";
        };
    }

    private static String requiredChannel(String value) {
        String channel = value == null ? "" : value.trim();
        if (!CHANNEL_ID.matcher(channel).matches()) {
            throw new IllegalArgumentException("Slack 채널을 목록에서 다시 선택해 주세요.");
        }
        return channel;
    }

    private static String ensureExtension(String name) {
        String safe = (name == null || name.isBlank()) ? "hub-document" : name.trim();
        return safe.toLowerCase(java.util.Locale.ROOT).matches(".*\\.[a-z0-9]{1,8}$") ? safe : safe + ".txt";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
