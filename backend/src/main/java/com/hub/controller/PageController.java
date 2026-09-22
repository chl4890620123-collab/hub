package com.hub.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the React SPA's built shell (web/dist/index.html, copied into static/ at image build time)
 * for every client-side route react-router owns. The same shell goes out whether or not the visitor
 * is logged in - auth is enforced only at the /api/** layer, and the app itself redirects to /login
 * once it learns from /api/me that there is no session (see SecurityConfig's matching permitAll list
 * and its bearerTokenResolver exemptions - both must stay in sync with SPA_ROUTES). Built JS/CSS/font
 * chunks under /assets/** are plain files served by Spring's normal static-resource handler, not this.
 */
@RestController
public class PageController {
    @GetMapping(value = {
            "/", "/login", "/signup", "/signup/member", "/signup/admin",
            "/search", "/ask", "/context", "/todos", "/review",
            "/documents", "/meetings", "/sheets", "/connectors", "/account",
            "/admin", "/admin/members", "/admin/reassign", "/admin/users",
            "/admin/search", "/admin/security", "/admin/history",
    })
    public ResponseEntity<Resource> spa() {
        Resource index = new ClassPathResource("static/index.html");
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(index);
    }
}
