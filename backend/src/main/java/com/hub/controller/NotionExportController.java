package com.hub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.NotionExportService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/documents/{documentId}/notion-export")
public class NotionExportController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final NotionExportService exports;
    private final AuditRepository audit;
    private final ObjectMapper json;

    public NotionExportController(CurrentUserService current, ProjectAccessService access,
                                  NotionExportService exports, AuditRepository audit, ObjectMapper json) {
        this.current = current;
        this.access = access;
        this.exports = exports;
        this.audit = audit;
        this.json = json;
    }

    @PostMapping
    public NotionExportService.ExportResult export(@PathVariable long projectId,
                                                    @PathVariable long documentId,
                                                    @RequestBody NotionExportService.ExportRequest request,
                                                    Authentication authentication) {
        User user = current.requireOperational(authentication);
        // Write-back is intentionally admin-only, same as the GitHub export path.
        access.requireAdmin(projectId, user);
        var result = exports.export(projectId, documentId, user.id(), request);
        try {
            audit.add(user.id(), projectId, "NOTION_EXPORT", "DOCUMENT", documentId,
                    json.writeValueAsString(Map.of("pageId", result.pageId(), "status", result.status())));
        } catch (Exception ignored) {
            audit.add(user.id(), projectId, "NOTION_EXPORT", "DOCUMENT", documentId, "{}");
        }
        return result;
    }
}
