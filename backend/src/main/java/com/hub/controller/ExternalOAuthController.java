package com.hub.controller;

import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.ExternalOAuthService;
import com.hub.service.ProjectAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController
public class ExternalOAuthController {
    private final CurrentUserService current;private final ProjectAccessService access;private final ExternalOAuthService oauth;
    public ExternalOAuthController(CurrentUserService current,ProjectAccessService access,ExternalOAuthService oauth){this.current=current;this.access=access;this.oauth=oauth;}
    @GetMapping("/api/projects/{projectId}/connectors/{type}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable long projectId,@PathVariable String type,Authentication auth,HttpServletRequest request){User u=current.requireOperational(auth);access.requireAdmin(projectId,u);String t=type.toUpperCase();String redirect=request.getRequestURL().toString().replace("/api/projects/"+projectId+"/connectors/"+type+"/authorize","/api/connectors/oauth/callback");return ResponseEntity.status(302).location(URI.create(oauth.authorize(u.id(),projectId,t,redirect))).build();}
    @GetMapping("/api/connectors/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam String code,@RequestParam String state){var p=oauth.callback(code,state);return ResponseEntity.status(302).location(URI.create("/?connected="+p.type()+"&project="+p.projectId())).build();}
    @GetMapping("/api/projects/{projectId}/connectors/{type}/oauth-status")
    public Map<String,Object> status(@PathVariable long projectId,@PathVariable String type,Authentication auth){User u=current.requireOperational(auth);access.requireAdmin(projectId,u);return Map.of("connected",oauth.connected(u.id(),type.toUpperCase()));}
}