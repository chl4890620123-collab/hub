// Browser file upload stores the document first. AI analysis starts only after explicit confirmation.
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
    public Map<String,Object> upload(@PathVariable long projectId, @RequestPart("file") MultipartFile file,
                                     Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        long versionId = documents.upload(projectId, file, user);
        long documentId = repository.documentIdForVersion(versionId);
        return Map.of("versionId", versionId, "documentId", documentId, "status", "UPLOADED");
    }

    public record ManualText(String title, String text, LocalDate sourceDate, LocalDate dueDate, Long assigneeId) {}

    /**
     * The dashboard's "빠른 업무 메모" quick-note form is the only caller: it stores the text as a
     * document version, queues AI analysis immediately (no separate upload/analyze confirmation step,
     * unlike file upload), and - when both dueDate and assigneeId are given - also creates a confirmed
     * follow-up todo right away, independent of whatever the AI analysis later suggests.
     */
    @PostMapping("/manual")
    public ResponseEntity<Map<String,Object>> manual(@PathVariable long projectId, @RequestBody ManualText request, Authentication auth) {
        User user = current.requireOperational(auth); access.requireAccess(projectId, user);
        validateFollowUp(request.dueDate(), request.assigneeId());
        long versionId = documents.manualText(projectId, request.title(), request.text(), user);
        long jobId = jobs.queueDocument(projectId, versionId, request.sourceDate());
        Map<String,Object> body = scheduleFollowUp(projectId, versionId, request.title(), request.text(), request.dueDate(), request.assigneeId(), user);
        body.put("versionId", versionId); body.put("jobId", jobId); body.put("status", "PENDING");
        body.put("documentId", repository.documentIdForVersion(versionId));
        return ResponseEntity.accepted().body(body);
    }

    private static void validateFollowUp(LocalDate dueDate, Long assigneeId) {
        if ((dueDate == null) != (assigneeId == null)) {
            throw new IllegalArgumentException("후속 할 일을 만들려면 담당자와 기한을 함께 선택해 주세요.");
        }
    }

    /** dueDate and assigneeId are both optional; when both are set, save a scheduled follow-up task too. */
    private Map<String,Object> scheduleFollowUp(long projectId, long versionId, String title, String text, LocalDate dueDate, Long assigneeId, User user) {
        Map<String,Object> body = new HashMap<>();
        if (dueDate != null) {
            long todoId = todos.createManual(projectId, versionId, title, text, assigneeId, dueDate, user);
            body.put("todoId", todoId);
        }
        return body;
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
