// Browser file upload stores the document first. AI analysis starts only after explicit confirmation.
package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.DocumentRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.DocumentService;
import com.hub.service.ProcessingJobService;
import com.hub.service.ProjectAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final DocumentService documents;
    private final ProcessingJobService jobs;
    private final DocumentRepository repository;

    public DocumentController(CurrentUserService current, ProjectAccessService access, DocumentService documents,
                              ProcessingJobService jobs, DocumentRepository repository) {
        this.current = current; this.access = access; this.documents = documents; this.jobs = jobs;
        this.repository = repository;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Map<String,Object> upload(@PathVariable long projectId, @RequestPart("file") MultipartFile file,
                                     Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        long versionId = documents.upload(projectId, file, user);
        long documentId = repository.documentIdForVersion(versionId);
        return Map.of("versionId", versionId, "documentId", documentId, "status", "UPLOADED");
    }

    public record DocumentEdit(String title, String text) {}

    @PutMapping("/{documentId}")
    public ResponseEntity<Map<String,Object>> edit(@PathVariable long projectId, @PathVariable long documentId,
                                                    @RequestBody DocumentEdit request, Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        long versionId = documents.edit(projectId, documentId, request.title(), request.text(), user);
        long jobId = jobs.queueDocument(projectId, versionId, null);
        return ResponseEntity.accepted().body(Map.<String,Object>of("versionId", versionId, "jobId", jobId, "status", "PENDING"));
    }

    public record ReviseDraftRequest(Long meetingDocumentId) {}

    @PostMapping("/{documentId}/revise-draft")
    public Map<String,Object> reviseDraft(@PathVariable long projectId, @PathVariable long documentId,
                                          @RequestBody ReviseDraftRequest request, Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        String revisedText = documents.reviseDraftFromMeeting(projectId, documentId, request.meetingDocumentId(), user);
        return Map.of("revisedText", revisedText);
    }

    @PostMapping("/{versionId}/analyze")
    public ResponseEntity<Map<String,Object>> analyze(@PathVariable long projectId, @PathVariable long versionId,
                                                       @RequestParam(required = false) LocalDate sourceDate,
                                                       @RequestParam(defaultValue = "false") boolean force,
                                                       Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        if (repository.projectIdForVersion(versionId) != projectId) throw new IllegalArgumentException("현재 프로젝트의 문서 버전이 아닙니다.");
        long jobId = jobs.queueDocument(projectId, versionId, sourceDate, force);
        return ResponseEntity.accepted().body(Map.<String,Object>of("versionId", versionId, "jobId", jobId, "status", "PENDING"));
    }
}
