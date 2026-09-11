package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.DocumentRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class DocumentQueryController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final DocumentRepository documents;
    private final AuditRepository audit;

    public DocumentQueryController(CurrentUserService currentUser,
                                   ProjectAccessService projectAccess,
                                   DocumentRepository documents,
                                   AuditRepository audit) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.documents = documents;
        this.audit = audit;
    }

    @GetMapping("/api/projects/{projectId}/documents")
    public List<Map<String, Object>> list(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return documents.listDocuments(projectId);
    }

    @GetMapping("/api/chunks/{chunkId}")
    public Map<String,Object> chunk(@PathVariable long chunkId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        Map<String,Object> row = documents.chunkDetail(chunkId);
        Object projectValue = row.get("project_id");
        if (projectValue == null) projectValue = row.get("PROJECT_ID");
        long projectId = ((Number) projectValue).longValue();
        projectAccess.requireAccess(projectId, user);
        return row;
    }

    @GetMapping("/api/versions/{versionId}")
    public Map<String,Object> version(@PathVariable long versionId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = documents.projectIdForVersion(versionId);
        projectAccess.requireAccess(projectId, user);
        return documents.versionDetail(versionId);
    }

    @GetMapping("/api/documents/{documentId}/versions")
    public List<Map<String, Object>> versions(@PathVariable long documentId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = documents.projectIdForDocument(documentId);
        projectAccess.requireAccess(projectId, user);
        return documents.listVersions(documentId);
    }

    @DeleteMapping("/api/documents/{documentId}")
    public Map<String, Object> archive(@PathVariable long documentId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = documents.projectIdForDocument(documentId);
        projectAccess.requireAdmin(projectId, user);
        documents.archive(documentId);
        audit.add(user.id(), projectId, "DOCUMENT_ARCHIVE", "DOCUMENT", documentId, "{}");
        return Map.of("status", "ARCHIVED");
    }
}
