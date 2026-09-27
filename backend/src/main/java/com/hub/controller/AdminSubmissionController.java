package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AdminSubmissionRepository;
import com.hub.service.AdminSubmissionService;
import com.hub.service.CurrentUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class AdminSubmissionController {
    private final CurrentUserService currentUser;
    private final AdminSubmissionService service;

    public AdminSubmissionController(CurrentUserService currentUser, AdminSubmissionService service) {
        this.currentUser = currentUser;
        this.service = service;
    }

    public record SubmissionView(
            long id,
            long projectId,
            long senderId,
            String title,
            String note,
            String externalUrl,
            String fileName,
            String contentType,
            Long sizeBytes,
            String createdAt
    ) {
        static SubmissionView of(AdminSubmissionRepository.Submission row) {
            return new SubmissionView(
                    row.id(), row.projectId(), row.senderId(), row.title(), row.note(), row.externalUrl(),
                    row.fileName(), row.contentType(), row.sizeBytes(), row.createdAt().toString()
            );
        }
    }

    @PostMapping(value = "/api/projects/{projectId}/admin-submissions", consumes = "multipart/form-data")
    public Map<String, Object> submit(@PathVariable long projectId,
                                      @RequestParam String title,
                                      @RequestParam(required = false) String note,
                                      @RequestParam(required = false) String url,
                                      @RequestPart(value = "file", required = false) MultipartFile file,
                                      Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long id = service.submit(projectId, title, note, url, file, user);
        return Map.of("id", id, "status", "SUBMITTED");
    }

    @GetMapping("/api/projects/{projectId}/admin-submissions")
    public List<SubmissionView> list(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        return service.list(projectId, user).stream().map(SubmissionView::of).toList();
    }

    @GetMapping("/api/admin-submissions/{submissionId}/download")
    public ResponseEntity<byte[]> download(@PathVariable long submissionId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        var file = service.download(submissionId, user);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(file.contentType() != null ? MediaType.parseMediaType(file.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(file.data());
    }

    @DeleteMapping("/api/admin-submissions/{submissionId}")
    public Map<String, Object> delete(@PathVariable long submissionId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        service.delete(submissionId, user);
        return Map.of("status", "DELETED");
    }
}
