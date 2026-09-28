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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Write-side Notion integration kept separate from the read-only connector (NotionConnector only
 * ever reads page content). Creates a brand new child page under a page the account can already see.
 *
 * Notion has no OAuth `scope` parameter (unlike GitHub/Slack) - whether this integration may write at
 * all is controlled entirely by the "Capabilities" setting of the integration at
 * notion.so/my-integrations, configured outside this codebase. If that setting does not include
 * "Insert content", every export attempt fails with HTTP 403 regardless of code correctness - this
 * is a one-time manual step the workspace owner must complete, not something a token/scope change
 * can fix.
 */
@Service
public class NotionExportService {
    private static final Pattern ID = Pattern.compile(
            "(?:[A-Fa-f0-9]{32}|[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12})");
    private static final String API_VERSION = "2022-06-28";
    private static final int MAX_BLOCKS = 100;
    private static final int MAX_RICH_TEXT_CHARS = 2000;

    private final RestClient client;
    private final ObjectMapper json;
    private final ExternalOAuthTokenStore tokens;
    private final ExportDocumentLoader loader;

    public NotionExportService(RestClient.Builder builder, ObjectMapper json,
                               ExternalOAuthTokenStore tokens, ExportDocumentLoader loader) {
        this.client = builder.baseUrl("https://api.notion.com/v1")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .build();
        this.json = json;
        this.tokens = tokens;
        this.loader = loader;
    }

    public record ExportRequest(String parentPageId, String title) {}
    public record ExportResult(String status, String pageId, String url) {}

    public ExportResult export(long projectId, long documentId, long userId, ExportRequest request) {
        if (request == null) throw new IllegalArgumentException("Notion 저장 요청이 비어 있습니다.");
        String parentPageId = requiredPageId(request.parentPageId());
        String token = tokens.get(userId, "NOTION");
        if (token == null || token.isBlank()) throw new IllegalStateException("Notion 계정을 먼저 연결해 주세요.");

        var loaded = loader.load(projectId, documentId);
        String title = blank(request.title()) ? loaded.meta().originalName() : request.title().trim();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parent", Map.of("page_id", parentPageId));
        body.put("properties", Map.of("title", Map.of("title", List.of(textRun(title)))));
        body.put("children", chunkToBlocks(loaded.content()));

        JsonNode created = post("/pages", body, token);
        return new ExportResult("CREATED", created.path("id").asText(""), blankToNull(created.path("url").asText("")));
    }

    /** Notion caps a page-create call at 100 children and 2000 chars per rich_text run. */
    private static List<Map<String, Object>> chunkToBlocks(String text) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (String paragraph : text.split("\\n{2,}")) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()) continue;
            for (int start = 0; start < trimmed.length() && blocks.size() < MAX_BLOCKS; start += MAX_RICH_TEXT_CHARS) {
                int end = Math.min(trimmed.length(), start + MAX_RICH_TEXT_CHARS);
                blocks.add(paragraphBlock(trimmed.substring(start, end)));
            }
            if (blocks.size() >= MAX_BLOCKS) break;
        }
        if (blocks.isEmpty()) {
            String fallback = text.isBlank() ? " " : text.substring(0, Math.min(text.length(), MAX_RICH_TEXT_CHARS));
            blocks.add(paragraphBlock(fallback));
        }
        boolean truncated = blocks.size() >= MAX_BLOCKS;
        if (truncated) {
            blocks = new ArrayList<>(blocks.subList(0, MAX_BLOCKS - 1));
            blocks.add(paragraphBlock("(내용이 길어 앞부분만 반영되었습니다. 전체 내용은 Hub에서 확인해 주세요.)"));
        }
        return blocks;
    }

    private static Map<String, Object> paragraphBlock(String text) {
        return Map.of("object", "block", "type", "paragraph",
                "paragraph", Map.of("rich_text", List.of(textRun(text))));
    }

    private static Map<String, Object> textRun(String text) {
        return Map.of("type", "text", "text", Map.of("content", text));
    }

    private JsonNode post(String path, Object payload, String token) {
        try {
            String response = client.post().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("Notion-Version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(payload))
                    .retrieve().body(String.class);
            return json.readTree(response == null ? "{}" : response);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new IllegalStateException("Notion 연결이 만료되었거나 유효하지 않습니다. 다시 연결해 주세요.", e);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException(
                    "Notion 연결 앱에 이 페이지를 편집할 권한이 없습니다. notion.so/my-integrations 에서 연결 앱의 "
                            + "Capabilities에 'Insert content'를 추가하고, 대상 페이지에도 연결 앱이 공유되어 있는지 확인해 주세요.", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException("Notion 페이지를 찾을 수 없거나 연결 앱에 공유되지 않았습니다.", e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Notion 요청이 실패했습니다. (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Notion에 연결하지 못했습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Notion 응답을 처리하지 못했습니다.", e);
        }
    }

    private static String requiredPageId(String value) {
        String raw = value == null ? "" : value.trim();
        if (!ID.matcher(raw).matches()) throw new IllegalArgumentException("Notion 페이지를 목록에서 다시 선택해 주세요.");
        return raw;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
