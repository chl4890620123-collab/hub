package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.DocumentRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.FileStorageService;
import com.hub.service.ProjectAccessService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class DocumentQueryController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final DocumentRepository documents;
    private final AuditRepository audit;
    private final FileStorageService storage;

    public DocumentQueryController(CurrentUserService currentUser,
                                   ProjectAccessService projectAccess,
                                   DocumentRepository documents,
                                   AuditRepository audit,
                                   FileStorageService storage) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.documents = documents;
        this.audit = audit;
        this.storage = storage;
    }

    @GetMapping("/api/projects/{projectId}/documents")
    public List<Map<String, Object>> list(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        // Document summary is for user-managed documents only. Connector/system snapshots and meeting
        // transcripts keep their DB/audit/search records but do not masquerade as editable documents.
        return documents.listDocuments(projectId).stream()
                .filter(row -> isUserManagedSource(stringValue(row, "source_type")))
                .toList();
    }

    @GetMapping("/api/projects/{projectId}/documents/meeting-transcripts")
    public List<Map<String, Object>> meetingTranscripts(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return documents.listDocuments(projectId).stream()
                .filter(row -> "MEETING_TRANSCRIPT".equalsIgnoreCase(stringValue(row, "source_type")))
                .toList();
    }

    private static boolean isUserManagedSource(String sourceType) {
        return switch (sourceType == null ? "" : sourceType.toUpperCase(java.util.Locale.ROOT)) {
            case "FILE", "MANUAL", "MANUAL_TEXT", "LOCAL_PC" -> true;
            default -> false;
        };
    }

    private static String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase());
        return value == null ? "" : String.valueOf(value);
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

    @GetMapping("/api/documents/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable long documentId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = documents.projectIdForDocument(documentId);
        projectAccess.requireAccess(projectId, user);
        var file = documents.findFile(documentId).orElseThrow(() -> new IllegalArgumentException("자료를 찾을 수 없습니다."));
        if (file.storagePath() == null) throw new IllegalArgumentException("원본 파일이 없는 자료입니다.");
        byte[] data = storage.readTrusted(file.storagePath());
        String fileName = file.originalName() == null ? "download" : file.originalName();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(data);
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

    @PostMapping("/api/documents/{documentId}/restore")
    public Map<String, Object> restore(@PathVariable long documentId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = documents.projectIdForDocument(documentId);
        projectAccess.requireAdmin(projectId, user);
        documents.restore(documentId);
        audit.add(user.id(), projectId, "DOCUMENT_RESTORE", "DOCUMENT", documentId, "{}");
        return Map.of("status", "ACTIVE");
    }

    @DeleteMapping("/api/documents/{documentId}/permanent")
    public Map<String, Object> deletePermanently(@PathVariable long documentId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = documents.projectIdForDocument(documentId);
        projectAccess.requireAdmin(projectId, user);
        var file = documents.findFile(documentId).orElseThrow(() -> new IllegalArgumentException("자료를 찾을 수 없습니다."));
        // A permanent delete must not claim success if the original bytes are still on disk.
        // Delete the trusted local file first; only then remove the DB/search records.
        storage.deleteStrict(file.storagePath());
        documents.deletePermanently(documentId);
        audit.add(user.id(), projectId, "DOCUMENT_DELETE", "DOCUMENT", documentId, "{}");
        return Map.of("status", "DELETED");
    }
}
