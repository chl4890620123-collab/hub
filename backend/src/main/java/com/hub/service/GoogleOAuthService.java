package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleOAuthService {
    // "email" is requested so the screen can name the Google account that was linked, not just say "connected".
    private static final String SCOPE = "https://www.googleapis.com/auth/drive.readonly email";
    private final HubProperties props;
    private final GoogleAccessTokenProvider tokens;
    private final RestClient client;
    private final ObjectMapper json;
    private final Map<String, PendingState> states = new ConcurrentHashMap<>();

    public GoogleOAuthService(HubProperties props, GoogleAccessTokenProvider tokens, RestClient.Builder builder, ObjectMapper json) {
        this.props = props; this.tokens = tokens; this.json = json;
        this.client = builder.baseUrl("https://oauth2.googleapis.com")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(30))).build();
    }

    public String authorizationUrl(long userId, long projectId, String redirectUri) {
        if (blank(props.googleClientId()) || blank(props.googleClientSecret()))
            throw new IllegalStateException("Google OAuth 설정이 필요합니다. GOOGLE_CLIENT_ID와 GOOGLE_CLIENT_SECRET을 입력해 주세요.");
        String state = UUID.randomUUID().toString();
        states.put(state, new PendingState(userId, projectId, redirectUri, Instant.now().plusSeconds(600)));
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", props.googleClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                // Without select_account an already signed-in browser skips straight back, giving the
                // operator no chance to pick which Google account to link.
                .queryParam("prompt", "select_account consent")
                .queryParam("state", state).build().encode().toUriString();
    }

    public PendingState consume(String state, String code) {
        PendingState pending = states.remove(state);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("Google 로그인 요청이 만료되었습니다.");
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("code", code); form.add("client_id", props.googleClientId()); form.add("client_secret", props.googleClientSecret());
            form.add("redirect_uri", pending.redirectUri()); form.add("grant_type", "authorization_code");
            String body = client.post().uri("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class);
            JsonNode node = json.readTree(body == null ? "{}" : body);
            String refresh = node.path("refresh_token").asText("");
            String access = node.path("access_token").asText("");
            if (refresh.isBlank() || access.isBlank()) throw new IllegalStateException("Google 토큰을 발급받지 못했습니다.");
            tokens.connect(pending.userId(), pending.projectId(), refresh, access, node.path("expires_in").asLong(3600), accountEmail(access));
            return pending;
        } catch (Exception e) { throw new IllegalStateException("Google 로그인 처리에 실패했습니다.", e); }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public record PendingState(long userId, long projectId, String redirectUri, Instant expiresAt) {}

    /** Best-effort: a missing email must not fail a connection that otherwise succeeded. */
    private String accountEmail(String accessToken) {
        try {
            String body = RestClient.create().get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(String.class);
            String email = json.readTree(body == null ? "{}" : body).path("email").asText("");
            return email.isBlank() ? null : email;
        } catch (Exception ignored) {
            return null;
        }
    }
}