package com.hub.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
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

}
