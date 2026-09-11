package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supplies a Google Drive access token without persisting credentials in the browser or database.
 * A configured refresh token is preferred for long-running installations; a static access token
 * remains supported for short local demos.
 */
@Service
public class GoogleAccessTokenProvider {
    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(60);

    private final HubProperties props;
    private final RestClient tokenClient;
    private final ObjectMapper json;
    private volatile String cachedAccessToken;
    private volatile Instant cachedUntil = Instant.EPOCH;
    private final Map<Long, UserCredential> userCredentials = new ConcurrentHashMap<>();

    public void connect(long userId, String refreshToken, String accessToken, long expiresIn) {
        userCredentials.put(userId, new UserCredential(refreshToken, accessToken,
                Instant.now().plusSeconds(Math.max(120, expiresIn)).minus(EXPIRY_SAFETY_MARGIN)));
    }

    public boolean connected(long userId) { return userCredentials.containsKey(userId); }

    public String accessToken(long userId) {
        UserCredential credential = userCredentials.get(userId);
        if (credential == null) return accessToken();
        if (credential.accessToken() != null && Instant.now().isBefore(credential.cachedUntil())) return credential.accessToken();
        synchronized (credential) {
            if (credential.accessToken() != null && Instant.now().isBefore(credential.cachedUntil())) return credential.accessToken();
            return refresh(credential.refreshToken(), userId);
        }
    }

    public GoogleAccessTokenProvider(HubProperties props, RestClient.Builder builder, ObjectMapper json) {
        this.props = props;
        this.json = json;
        this.tokenClient = builder
                .baseUrl("https://oauth2.googleapis.com")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(30)))
                .build();
    }

    public String accessToken() {
        if (hasRefreshCredentials()) {
            String token = cachedAccessToken;
            if (token != null && !token.isBlank() && Instant.now().isBefore(cachedUntil)) return token;
            synchronized (this) {
                token = cachedAccessToken;
                if (token != null && !token.isBlank() && Instant.now().isBefore(cachedUntil)) return token;
                return refresh(trim(props.googleRefreshToken()), null);
            }
        }
        String staticToken = trim(props.googleAccessToken());
        if (!staticToken.isBlank()) return staticToken;
        throw new IllegalStateException(
                "Google Drive credentials are not configured. Set GOOGLE_ACCESS_TOKEN or GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET/GOOGLE_REFRESH_TOKEN."
        );
    }

    private boolean hasRefreshCredentials() {
        return !trim(props.googleClientId()).isBlank()
                && !trim(props.googleClientSecret()).isBlank()
                && !trim(props.googleRefreshToken()).isBlank();
    }

    private String refresh(String refreshToken, Long userId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", trim(props.googleClientId()));
        form.add("client_secret", trim(props.googleClientSecret()));
        form.add("refresh_token", trim(refreshToken));
        form.add("grant_type", "refresh_token");
        try {
            String body = tokenClient.post()
                    .uri("/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode node = json.readTree(body == null ? "{}" : body);
            String token = node.path("access_token").asText("").trim();
            if (token.isBlank()) throw new IllegalStateException("Google token refresh returned no access token");
            long expiresIn = Math.max(120, node.path("expires_in").asLong(3600));
            cachedAccessToken = token;
            cachedUntil = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_SAFETY_MARGIN);
            if (userId != null) userCredentials.put(userId, new UserCredential(refreshToken, token, cachedUntil));
            return token;
        } catch (RestClientException e) {
            throw new IllegalStateException("Google Drive token refresh request failed", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Google token refresh response", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record UserCredential(String refreshToken, String accessToken, Instant cachedUntil) {}
}
