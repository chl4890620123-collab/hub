package com.hub.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read-only Notion page importer. The integration must already have access to the page.
 * Hub stores a text snapshot and keeps the original Notion page URL for one-click opening.
 */
@Component
public class NotionConnector implements ReadOnlyConnector {
    private static final Pattern ID = Pattern.compile("(?:[A-Fa-f0-9]{32}|[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12})");
    private static final String API_VERSION = "2022-06-28";
    private final RestClient client;
    private final ObjectMapper json;

    public NotionConnector(RestClient.Builder builder, ObjectMapper json) {
        this.client = builder.baseUrl("https://api.notion.com/v1")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .build();
        this.json = json;
    }

    @Override public String type() { return "NOTION"; }

    @Override
    public List<ExternalContent> fetch(String scope, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Notion access token is required");
        String pageId = normalizeId(scope);
        JsonNode page = get("/pages/" + pageId, token);
        String title = pageTitle(page);
        String url = page.path("url").asText("https://www.notion.so/" + pageId.replace("-", ""));
        StringBuilder text = new StringBuilder();
        readBlocks(pageId, token, text, 0);
        String content = text.toString().trim();
        if (content.isBlank()) content = title;
        OffsetDateTime created = ConnectorSupport.date(page.path("created_time").asText(""));
        return List.of(new ExternalContent(
                pageId, "NOTION_PAGE", title, content, null, "text/plain", "", url, created,
                Map.of("pageId", pageId, "location", "Notion / " + title)
        ));
    }

    private void readBlocks(String blockId, String token, StringBuilder out, int depth) {
        if (depth > 3) return;
        String cursor = "";
        int pages = 0;
        do {
            String uri = "/blocks/" + blockId + "/children?page_size=100" + (cursor.isBlank() ? "" : "&start_cursor=" + cursor);
            JsonNode response = get(uri, token);
            for (JsonNode block : response.path("results")) {
                String type = block.path("type").asText("");
                JsonNode body = block.path(type);
                String line = richText(body.path("rich_text"));
                if (!line.isBlank()) out.append(line).append('\n');
                if (block.path("has_children").asBoolean(false)) readBlocks(block.path("id").asText(), token, out, depth + 1);
            }
            cursor = response.path("has_more").asBoolean(false) ? response.path("next_cursor").asText("") : "";
            pages++;
        } while (!cursor.isBlank() && pages < 5);
    }

    private JsonNode get(String uri, String token) {
        try {
            String body = client.get().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("Notion-Version", API_VERSION)
                    .retrieve().body(String.class);
            return ConnectorSupport.json(json, body, "Invalid Notion response");
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new IllegalStateException("Notion 연결이 만료되었거나 토큰이 유효하지 않습니다. 다시 연결해 주세요.", e);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException("Notion Integration에 해당 페이지 접근 권한이 없습니다. 페이지의 Connections에서 Integration을 공유해 주세요.", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException("Notion 페이지를 찾을 수 없거나 Integration에 공유되지 않았습니다.", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Notion API request failed", e);
        }
    }

    private static String pageTitle(JsonNode page) {
        JsonNode props = page.path("properties");
        var names = props.fieldNames();
        while (names.hasNext()) {
            JsonNode value = props.path(names.next());
            if ("title".equals(value.path("type").asText())) {
                String title = richText(value.path("title"));
                if (!title.isBlank()) return title;
            }
        }
        return "Notion page";
    }

    private static String richText(JsonNode array) {
        if (!array.isArray()) return "";
        List<String> parts = new ArrayList<>();
        for (JsonNode node : array) {
            String plain = node.path("plain_text").asText("");
            if (!plain.isBlank()) parts.add(plain);
        }
        return String.join("", parts).trim();
    }

    private static String normalizeId(String raw) {
        String value = raw == null ? "" : raw.trim();
        java.util.regex.Matcher matcher = ID.matcher(value);
        String found = null;
        while (matcher.find()) found = matcher.group();
        if (found == null) throw new IllegalArgumentException("Notion scope must be a page id or page URL");
        return found;
    }


    // 100/page - caps a runaway workspace at 1000 shared pages rather than looping forever.
    private static final int MAX_TARGET_PAGES = 10;

    /** Pages this integration has been shared with. Follows Notion's start_cursor pagination
     * instead of stopping at the first page, or a workspace with >100 shared pages silently lost
     * everything past it. */
    @Override
    public List<ConnectorTarget> targets(String token) {
        List<ConnectorTarget> out = new ArrayList<>();
        String cursor = null;
        for (int pageIndex = 0; pageIndex < MAX_TARGET_PAGES; pageIndex++) {
            JsonNode body;
            try {
                String requestBody = cursor == null || cursor.isBlank()
                        ? "{\"filter\":{\"value\":\"page\",\"property\":\"object\"},\"page_size\":100}"
                        : "{\"filter\":{\"value\":\"page\",\"property\":\"object\"},\"page_size\":100,\"start_cursor\":\""
                                + cursor.replace("\"", "\\\"") + "\"}";
                String response = client.post().uri("/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Notion-Version", API_VERSION)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve().body(String.class);
                body = ConnectorSupport.json(json, response, "Invalid Notion response");
            } catch (RestClientException e) {
                throw new IllegalStateException("Notion API request failed", e);
            }
            JsonNode results = body.path("results");
            if (results.isArray()) {
                for (JsonNode page : results) {
                    String id = page.path("id").asText("");
                    if (id.isBlank()) continue;
                    out.add(new ConnectorTarget(id, pageTitle(page), "Notion 페이지",
                            page.path("url").asText("https://www.notion.so/" + id.replace("-", ""))));
                }
            }
            cursor = body.path("has_more").asBoolean(false) ? body.path("next_cursor").asText("") : "";
            if (cursor.isBlank()) break;
        }
        return out;
    }
}
