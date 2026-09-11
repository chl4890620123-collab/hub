package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.LocalPcReaderService;
import com.hub.service.ProcessingJobService;
import com.hub.service.ProjectAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** ADMIN-only import of files from server-configured read-only PC folders. */
@RestController
@RequestMapping("/api/projects/{projectId}/local-reader")
public class LocalPcReaderController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final LocalPcReaderService reader;
    private final ProcessingJobService jobs;
    private final AuditRepository audit;

    public LocalPcReaderController(CurrentUserService current, ProjectAccessService access,
                                   LocalPcReaderService reader, ProcessingJobService jobs, AuditRepository audit) {
        this.current = current;
        this.access = access;
        this.reader = reader;
        this.jobs = jobs;
        this.audit = audit;
    }

    @GetMapping("/files")
    public Map<String, Object> files(@PathVariable long projectId,
                                     @RequestParam(defaultValue = "200") int limit,
                                     Authentication authentication) {
        User user = current.requireOperational(authentication);
        access.requireAdmin(projectId, user);
        return Map.of("roots", reader.roots(), "files", reader.listFiles(limit));
    }

    public record ImportRequest(String rootId, String relativePath, LocalDate sourceDate) {}

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFile(@PathVariable long projectId,
                                                           @RequestBody ImportRequest request,
                                                           Authentication authentication) {
        User user = current.requireOperational(authentication);
        access.requireAdmin(projectId, user);
        var imported = reader.importFile(projectId, request.rootId(), request.relativePath(), user);
        long jobId = jobs.queueDocument(projectId, imported.versionId(), request.sourceDate());
        audit.add(user.id(), projectId, "LOCAL_PC_IMPORT", "DOCUMENT_VERSION", imported.versionId(),
                "{\"rootId\":\"" + imported.rootId() + "\"}");
        return ResponseEntity.accepted().body(Map.of(
                "versionId", imported.versionId(),
                "jobId", jobId,
                "status", "PENDING",
                "name", imported.name()
        ));
    }
}
