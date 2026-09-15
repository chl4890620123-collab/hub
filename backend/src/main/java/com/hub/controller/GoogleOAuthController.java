package com.hub.controller;

import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.GoogleAccessTokenProvider;
import com.hub.config.HubProperties;
import com.hub.service.GoogleOAuthService;
import com.hub.service.ProjectAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class GoogleOAuthController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final GoogleOAuthService oauth;
    private final GoogleAccessTokenProvider tokens;
    private final HubProperties props;

    public GoogleOAuthController(CurrentUserService current, ProjectAccessService access, GoogleOAuthService oauth, GoogleAccessTokenProvider tokens, HubProperties props) {
        this.current = current; this.access = access; this.oauth = oauth; this.tokens = tokens; this.props = props;
    }

    @GetMapping("/api/projects/{projectId}/connectors/google/authorize")
    public ResponseEntity<Void> authorize(@PathVariable long projectId, Authentication authentication, HttpServletRequest request) {
        User user = current.requireOperational(authentication); access.requireAccess(projectId, user);
        String redirect = OAuthRedirects.callbackUri(props.publicBaseUrl(), request.getRequestURL().toString(),
                "/api/projects/" + projectId + "/connectors/google/authorize", "/api/connectors/google/callback");
        try {
            return ResponseEntity.status(302).location(URI.create(oauth.authorizationUrl(user.id(), projectId, redirect))).build();
        } catch (IllegalStateException notConfigured) {
            return connectorRedirect("failed", "not_configured", null);
        }
    }

    /**
     * Google sends back either ?code or ?error (a cancelled consent screen sends only the latter), so both
     * parameters are optional. A required 'code' turned a cancelled login into an unauthenticated error
     * page instead of a message the operator could read.
     */
    @GetMapping("/api/connectors/google/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) return connectorRedirect("failed", error, null);
        if (code == null || code.isBlank() || state == null || state.isBlank())
            return connectorRedirect("failed", "missing_code", null);
        try {
            var pending = oauth.consume(state, code);
            return connectorRedirect("connected", null, pending.projectId());
        } catch (RuntimeException failure) {
            return connectorRedirect("failed", "exchange_failed", null);
        }
    }

    private ResponseEntity<Void> connectorRedirect(String status, String reason, Long projectId) {
        StringBuilder target = new StringBuilder("/?connector=GOOGLE_DRIVE&status=").append(status);
        if (reason != null) target.append("&reason=").append(URLEncoder.encode(reason, StandardCharsets.UTF_8));
        if (projectId != null) target.append("&project=").append(projectId);
        return ResponseEntity.status(302).location(URI.create(target.toString())).build();
    }

    @GetMapping("/api/projects/{projectId}/connectors/google/status")
    public Map<String, Object> status(@PathVariable long projectId, Authentication authentication) {
        User user = current.requireOperational(authentication); access.requireAccess(projectId, user);
        return Map.of("connected", tokens.connected(user.id()), "linkedByUser", tokens.linkedByUser(user.id()));
    }
}