package com.hub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.GitHubExportService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/documents/{documentId}/github-export")
public class GitHubExportController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final GitHubExportService exports;
    private final AuditRepository audit;
    private final ObjectMapper json;

    public GitHubExportController(CurrentUserService current, ProjectAccessService access,
                                  GitHubExportService exports, AuditRepository audit, ObjectMapper json) {
        this.current = current;
        this.access = access;
        this.exports = exports;
        this.audit = audit;
        this.json = json;
    }

    @PostMapping
    public GitHubExportService.ExportResult export(@PathVariable long projectId,
                                                    @PathVariable long documentId,
                                                    @RequestBody GitHubExportService.ExportRequest request,
                                                    Authentication authentication) {
        User user = current.requireOperational(authentication);
        // Write-back is intentionally admin-only until a project explicitly delegates write access.
        access.requireAdmin(projectId, user);
        var result = exports.export(projectId, documentId, user.id(), request);
        try {
            audit.add(user.id(), projectId, "GITHUB_EXPORT", "DOCUMENT", documentId,
                    json.writeValueAsString(Map.of(
                            "repository", result.repository(),
                            "path", result.path(),
                            "status", result.status()
                    )));
        } catch (Exception ignored) {
            audit.add(user.id(), projectId, "GITHUB_EXPORT", "DOCUMENT", documentId, "{}");
        }
        return result;
    }
}
