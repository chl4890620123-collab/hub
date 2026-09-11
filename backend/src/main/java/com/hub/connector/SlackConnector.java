package com.hub.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Read-only Slack channel importer used by unified material search. */
@Component
public class SlackConnector implements ReadOnlyConnector {
    private static final Pattern CHANNEL_ID = Pattern.compile("[CDG][A-Z0-9]{8,}");
    private static final int MESSAGE_LIMIT = 50;

    private final RestClient client;
    private final ObjectMapper json;

    public SlackConnector(RestClient.Builder builder, ObjectMapper json) {
        this.client = builder
                .baseUrl("https://slack.com/api")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .build();
        this.json = json;
    }

    @Override
    public String type() {
        return "SLACK";
    }

    /**
     * Scope is a Slack channel id such as C0123456789. Hub only reads messages and stores a snapshot.
     * Opening the result uses Slack's official permalink when the token can resolve it.
     */
    @Override
    public List<ExternalContent> fetch(String channelId, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Slack access token is required");
        String channel = channelId == null ? "" : channelId.trim();
        if (!CHANNEL_ID.matcher(channel).matches()) throw new IllegalArgumentException("Slack scope must be a channel id");

        JsonNode info = get("/conversations.info?channel=" + encode(channel), token);
        String channelName = info.path("channel").path("name").asText(channel);
        JsonNode history = get("/conversations.history?channel=" + encode(channel) + "&limit=" + MESSAGE_LIMIT, token);
        JsonNode messages = history.path("messages");
        if (!messages.isArray()) return List.of();

        Map<String, String> userNames = new HashMap<>();
        List<ExternalContent> out = new ArrayList<>();
        for (JsonNode message : messages) {
            String text = message.path("text").asText("").trim();
            String ts = message.path("ts").asText("").trim();
            if (text.isBlank() || ts.isBlank()) continue;
            String userId = message.path("user").asText("");
            String author = userNames.computeIfAbsent(userId, id -> userDisplayName(id, token));
            String permalink = permalink(channel, ts, token);
            String location = "Slack / #" + channelName + " / " + humanTimestamp(ts);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("channelId", channel);
            metadata.put("channelName", channelName);
            metadata.put("timestamp", ts);
            metadata.put("threadTs", message.path("thread_ts").asText(""));
            metadata.put("location", location);

            out.add(new ExternalContent(
                    channel + ":" + ts,
                    "SLACK_MESSAGE",
                    "#" + channelName + " · " + abbreviate(text, 90),
                    text,
                    null,
                    "text/plain",
                    author,
                    permalink,
                    parseSlackTimestamp(ts),
                    metadata
            ));
        }
        return out;
    }

    private String userDisplayName(String userId, String token) {
        if (userId == null || userId.isBlank()) return "";
        try {
            JsonNode response = get("/users.info?user=" + encode(userId), token);
            JsonNode user = response.path("user");
            String display = user.path("profile").path("display_name").asText("").trim();
            if (!display.isBlank()) return display;
            String real = user.path("profile").path("real_name").asText("").trim();
            return real.isBlank() ? userId : real;
        } catch (RuntimeException ignored) {
            return userId;
        }
    }

    private String permalink(String channelId, String ts, String token) {
        try {
            String uri = "/chat.getPermalink?channel=" + encode(channelId) + "&message_ts=" + encode(ts);
            return get(uri, token).path("permalink").asText("");
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private JsonNode get(String uri, String token) {
        try {
            String body = client.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);
            JsonNode node = json.readTree(body == null ? "{}" : body);
            if (!node.path("ok").asBoolean(false)) {
                throw new IllegalStateException("Slack API error: " + node.path("error").asText("unknown_error"));
            }
            return node;
        } catch (RestClientException e) {
            throw new IllegalStateException("Slack API request failed", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Slack response", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static OffsetDateTime parseSlackTimestamp(String ts) {
        try {
            BigDecimal value = new BigDecimal(ts);
            long seconds = value.longValue();
            long nanos = value.subtract(BigDecimal.valueOf(seconds)).movePointRight(9).longValue();
            return Instant.ofEpochSecond(seconds, nanos).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String humanTimestamp(String ts) {
        OffsetDateTime parsed = parseSlackTimestamp(ts);
        return parsed == null ? ts : parsed.toString();
    }

    private static String abbreviate(String text, int max) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }
}
