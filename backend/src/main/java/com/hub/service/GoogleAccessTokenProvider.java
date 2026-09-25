package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.repository.ConnectorAccountRepository;
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
    private final ConnectorAccountRepository accounts;

    public void connect(long userId, long projectId, String refreshToken, String accessToken, long expiresIn) {
        connect(userId, projectId, refreshToken, accessToken, expiresIn, null);
    }

    public void connect(long userId, long projectId, String refreshToken, String accessToken, long expiresIn, String accountLabel) {
        accounts.save(userId, projectId, "GOOGLE_DRIVE", accessToken, refreshToken,
                Instant.now().plusSeconds(Math.max(120, expiresIn)).minus(EXPIRY_SAFETY_MARGIN), accountLabel);
    }

    /** The Google account that granted access, for display. */
    public String accountLabel(long userId) { return accounts.accountLabel(userId, "GOOGLE_DRIVE"); }

    public Long accountId(long userId) { return accounts.id(userId, "GOOGLE_DRIVE"); }

    /**
     * True when this account linked Google itself, or when the server holds shared credentials AND
     * an admin has explicitly opted into sharing them (hub.allow-shared-connector-fallback). Without
     * that opt-in, a refresh token left in the server config (e.g. from local setup) must never make
     * every other user look "connected" through someone else's Drive.
     */
    public boolean connected(long userId) {
        return accounts.connected(userId, "GOOGLE_DRIVE")
                || (props.allowSharedConnectorFallback() && (hasRefreshCredentials() || !trim(props.googleAccessToken()).isBlank()));
    }

    /** True only for a link this account made, so the screen can tell the two apart. */
    public boolean linkedByUser(long userId) { return accounts.connected(userId, "GOOGLE_DRIVE"); }

    public void disconnect(long userId) { accounts.disconnect(userId, "GOOGLE_DRIVE"); }

    public String accessToken(long userId) {
        var credential = accounts.find(userId, "GOOGLE_DRIVE").orElse(null);
        if (credential == null) {
            if (!props.allowSharedConnectorFallback())
                throw new IllegalStateException("이 계정에 Google Drive가 연결되어 있지 않습니다.");
            return accessToken();
        }
        if (credential.accessToken() != null && credential.expiresAt() != null
                && Instant.now().isBefore(credential.expiresAt())) return credential.accessToken();
        return refresh(credential.refreshToken(), userId);
    }

    public GoogleAccessTokenProvider(HubProperties props, RestClient.Builder builder, ObjectMapper json,
                                     ConnectorAccountRepository accounts) {
        this.props = props;
        this.json = json;
        this.accounts = accounts;
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
                "Google Drive 공용 연결 설정이 없습니다. 관리자에게 연결 설정을 확인해 달라고 요청해 주세요."
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
            if (token.isBlank()) throw new IllegalStateException("Google Drive 연결을 갱신하지 못했습니다. 다시 연결해 주세요.");
            long expiresIn = Math.max(120, node.path("expires_in").asLong(3600));
            cachedAccessToken = token;
            cachedUntil = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_SAFETY_MARGIN);
            if (userId != null) accounts.save(userId, null, "GOOGLE_DRIVE", token, refreshToken, cachedUntil);
            return token;
        } catch (RestClientException e) {
            throw new IllegalStateException("Google Drive 연결 갱신 요청에 실패했습니다. 다시 연결해 주세요.", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Google Drive 연결 갱신 응답을 처리하지 못했습니다. 다시 연결해 주세요.", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
