package com.hub.controller;

import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.GoogleAccessTokenProvider;
import com.hub.service.GoogleOAuthService;
import com.hub.service.ProjectAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
public class GoogleOAuthController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final GoogleOAuthService oauth;
    private final GoogleAccessTokenProvider tokens;

    public GoogleOAuthController(CurrentUserService current, ProjectAccessService access, GoogleOAuthService oauth, GoogleAccessTokenProvider tokens) {
        this.current = current; this.access = access; this.oauth = oauth; this.tokens = tokens;
    }

    @GetMapping("/api/projects/{projectId}/connectors/google/authorize")
    public ResponseEntity<Void> authorize(@PathVariable long projectId, Authentication authentication, HttpServletRequest request) {
        User user = current.requireOperational(authentication); access.requireAdmin(projectId, user);
        String redirect = request.getRequestURL().toString().replace("/api/projects/" + projectId + "/connectors/google/authorize", "/api/connectors/google/callback");
        return ResponseEntity.status(302).location(URI.create(oauth.authorizationUrl(user.id(), projectId, redirect))).build();
    }

    @GetMapping("/api/connectors/google/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        var pending = oauth.consume(state, code);
        return ResponseEntity.status(302).location(URI.create("/?google=connected&project=" + pending.projectId())).build();
    }

    @GetMapping("/api/projects/{projectId}/connectors/google/status")
    public Map<String, Object> status(@PathVariable long projectId, Authentication authentication) {
        User user = current.requireOperational(authentication); access.requireAdmin(projectId, user);
        return Map.of("connected", tokens.connected(user.id()));
    }
}