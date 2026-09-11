package com.hub.controller;

import com.hub.model.ProjectMemory;
import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import com.hub.service.ProjectMemoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/memory")
public class ProjectMemoryController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final ProjectMemoryService memoryService;

    public ProjectMemoryController(CurrentUserService currentUser,
                                   ProjectAccessService projectAccess,
                                   ProjectMemoryService memoryService) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.memoryService = memoryService;
    }

    @GetMapping
    public List<ProjectMemory> list(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return memoryService.list(projectId);
    }

    public record PutMemory(String key, String value, String type, String sourceType, Long sourceId) {}

    @PostMapping
    public Map<String, Object> put(@PathVariable long projectId,
                                   @RequestBody PutMemory request,
                                   Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAdmin(projectId, user);
        String key = required(request.key(), "Memory key", 200);
        String value = required(request.value(), "Memory value", 20_000);
        String type = request.type() == null || request.type().isBlank() ? "FACT" : request.type().trim();
        memoryService.put(projectId, key, value, type, request.sourceType(), request.sourceId(), user);
        return Map.of("status", "SAVED");
    }

    private static String required(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(label + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(label + " is too long");
        return normalized;
    }
}
