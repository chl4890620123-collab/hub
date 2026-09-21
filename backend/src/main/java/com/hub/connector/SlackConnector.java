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
    // 200/page - caps a runaway workspace at 2000 channels rather than looping forever.
    private static final int MAX_TARGET_PAGES = 10;

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
                throw new IllegalStateException(slackError(node.path("error").asText("unknown_error")));
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

    /** Public channels this token can read. Follows Slack's cursor pagination instead of stopping
     * at the first page, or a workspace with >200 channels silently lost everything past it. */
    @Override
    public List<ConnectorTarget> targets(String token) {
        List<ConnectorTarget> out = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_TARGET_PAGES; page++) {
            String uri = "/conversations.list?types=public_channel&exclude_archived=true&limit=200"
                    + (cursor == null || cursor.isBlank() ? "" : "&cursor=" + encode(cursor));
            JsonNode body = get(uri, token);
            JsonNode channels = body.path("channels");
            if (channels.isArray()) {
                for (JsonNode channel : channels) {
                    String id = channel.path("id").asText("");
                    String name = channel.path("name").asText("");
                    if (id.isBlank() || name.isBlank()) continue;
                    out.add(new ConnectorTarget(id, "#" + name, channel.path("purpose").path("value").asText("")));
                }
            }
            cursor = body.path("response_metadata").path("next_cursor").asText("");
            if (cursor.isBlank()) break;
        }
        return out;
    }

    /** Slack's error codes name a setup step; say which one instead of echoing the code. */
    private static String slackError(String code) {
        return switch (code) {
            case "not_in_channel" -> "이 채널에 Hub 앱을 먼저 초대해 주세요. 채널에서 /invite @Hub 를 실행하면 됩니다.";
            case "channel_not_found" -> "채널을 찾을 수 없습니다. 목록을 다시 불러온 뒤 선택해 주세요.";
            case "missing_scope", "not_allowed_token_type" -> "Slack 토큰 권한이 부족합니다. channels:history와 channels:read 권한을 확인해 주세요.";
            case "invalid_auth", "token_revoked", "account_inactive" -> "Slack 토큰이 유효하지 않습니다. 다시 연결해 주세요.";
            case "ratelimited" -> "Slack 요청이 많아 잠시 제한되었습니다. 잠시 후 다시 시도해 주세요.";
            default -> "Slack 자료를 가져오지 못했습니다 (" + code + ")";
        };
    }
}
