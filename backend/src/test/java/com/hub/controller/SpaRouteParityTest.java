package com.hub.controller;

import com.hub.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaRouteParityTest {
    @Test
    void springShellAndSecurityCoverEveryCurrentReactPageRoute() throws Exception {
        Set<String> expected = Set.of(
                "/",
                "/login", "/signup", "/signup/member", "/signup/admin",
                "/search", "/ask", "/context", "/todos", "/review",
                "/documents", "/meetings", "/sheets", "/connectors", "/notifications", "/account",
                "/admin", "/admin/members", "/admin/reassign", "/admin/users", "/admin/organization",
                "/admin/search", "/admin/security", "/admin/history"
        );

        GetMapping mapping = PageController.class.getMethod("spa").getAnnotation(GetMapping.class);
        Set<String> shellRoutes = new HashSet<>(Arrays.asList(mapping.value()));
        assertEquals(expected, shellRoutes, "PageController must serve the SPA shell for every React route");

        Field paths = SecurityConfig.class.getDeclaredField("SPA_PAGE_PATHS");
        paths.setAccessible(true);
        Set<String> securityRoutes = new HashSet<>(Arrays.asList((String[]) paths.get(null)));

        Set<String> expectedWithoutRoot = new HashSet<>(expected);
        expectedWithoutRoot.remove("/");
        assertEquals(expectedWithoutRoot, securityRoutes,
                "SecurityConfig SPA exemptions must stay in sync with PageController routes");
        assertTrue(shellRoutes.contains("/notifications"));
        assertTrue(shellRoutes.contains("/admin/organization"));
    }
}
