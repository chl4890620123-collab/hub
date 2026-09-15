// Browser file upload is the single document-ingestion endpoint.
// All document/manual uploads queue one deduplicated AI job; the web client never needs a second analyze click.
package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.DocumentRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.DocumentService;
import com.hub.service.ProcessingJobService;
import com.hub.service.ProjectAccessService;
import com.hub.service.TodoService;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final DocumentService documents;
    private final ProcessingJobService jobs;
    private final DocumentRepository repository;
    private final TodoService todos;

    public DocumentController(CurrentUserService current, ProjectAccessService access, DocumentService documents,
                              ProcessingJobService jobs, DocumentRepository repository, TodoService todos) {
        this.current = current; this.access = access; this.documents = documents; this.jobs = jobs;
        this.repository = repository; this.todos = todos;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String,Object>> upload(@PathVariable long projectId, @RequestPart("file") MultipartFile file,
                                     @RequestParam(required = false) LocalDate sourceDate,
                                     @RequestParam(required = false) LocalDate dueDate,
                                     @RequestParam(required = false) Long assigneeId,
                                     Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        long versionId = documents.upload(projectId, file, user);
        long jobId = jobs.queueDocument(projectId, versionId, sourceDate);
        Map<String,Object> body = scheduleFollowUp(projectId, versionId, file.getOriginalFilename(), dueDate, assigneeId, user);
        body.put("versionId", versionId); body.put("jobId", jobId); body.put("status", "PENDING");
        return ResponseEntity.accepted().body(body);
    }

    public record ManualText(String title, String text, LocalDate sourceDate, LocalDate dueDate, Long assigneeId) {}

    @PostMapping("/manual")
    public ResponseEntity<Map<String,Object>> manual(@PathVariable long projectId, @RequestBody ManualText request, Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        long versionId = documents.manualText(projectId, request.title(), request.text(), user);
        long jobId = jobs.queueDocument(projectId, versionId, request.sourceDate());
        Map<String,Object> body = scheduleFollowUp(projectId, versionId, request.title(), request.dueDate(), request.assigneeId(), user);
        body.put("versionId", versionId); body.put("jobId", jobId); body.put("status", "PENDING");
        body.put("documentId", repository.documentIdForVersion(versionId));
        return ResponseEntity.accepted().body(body);
    }

    public record ManualEdit(String title, String text) {}

    /** Only a document this account entered by hand (MANUAL_TEXT) can be edited this way. */
    @PutMapping("/{documentId}")
    public ResponseEntity<Map<String,Object>> editManual(@PathVariable long projectId, @PathVariable long documentId,
                                                          @RequestBody ManualEdit request, Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        long versionId = documents.manualEdit(projectId, documentId, request.title(), request.text(), user);
        long jobId = jobs.queueDocument(projectId, versionId, null);
        return ResponseEntity.accepted().body(Map.<String,Object>of("versionId", versionId, "jobId", jobId, "status", "PENDING"));
    }

    /** dueDate is optional on both save paths; when set, this turns the save into a scheduled follow-up task too. */
    private Map<String,Object> scheduleFollowUp(long projectId, long versionId, String title, LocalDate dueDate, Long assigneeId, User user) {
        Map<String,Object> body = new HashMap<>();
        if (dueDate != null) {
            long todoId = todos.createManual(projectId, versionId, title, assigneeId, dueDate, user);
            body.put("todoId", todoId);
        }
        return body;
    }

    @PostMapping("/{versionId}/analyze")
    public ResponseEntity<Map<String,Object>> analyze(@PathVariable long projectId, @PathVariable long versionId,
                                                       @RequestParam(required = false) LocalDate sourceDate, Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        if (repository.projectIdForVersion(versionId) != projectId) throw new IllegalArgumentException("Version does not belong to project");
        long jobId = jobs.queueDocument(projectId, versionId, sourceDate);
        return ResponseEntity.accepted().body(Map.<String,Object>of("versionId", versionId, "jobId", jobId, "status", "PENDING"));
    }
}
