package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.FileAttachmentRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.FileAttachmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

@RestController
public class FileAttachmentController {
    private final CurrentUserService currentUser;
    private final FileAttachmentService service;

    public FileAttachmentController(CurrentUserService currentUser, FileAttachmentService service) {
        this.currentUser = currentUser; this.service = service;
    }

    public record AttachmentView(long id, Long todoId, long senderId, Long recipientId, String fileName,
                                 String contentType, long sizeBytes, String note, boolean read, String createdAt) {
        static AttachmentView of(FileAttachmentRepository.Attachment a) {
            return new AttachmentView(a.id(), a.todoId(), a.senderId(), a.recipientId(), a.fileName(),
                    a.contentType(), a.sizeBytes(), a.note(), a.readAt() != null, a.createdAt().toString());
        }
    }

    @PostMapping(value = "/api/todos/{todoId}/attachments", consumes = "multipart/form-data")
    public Map<String, Object> attachToTodo(@PathVariable long todoId, @RequestPart("file") MultipartFile file,
                                            @RequestParam(required = false) String note, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        long id = service.attachToTodo(todoId, file, note, user);
        return Map.of("id", id, "status", "ATTACHED");
    }

    @GetMapping("/api/todos/{todoId}/attachments")
    public List<AttachmentView> listForTodo(@PathVariable long todoId, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        return service.listForTodo(todoId, user).stream().map(AttachmentView::of).toList();
    }

    public record SendFile(long recipientId, String note) {}

    @PostMapping(value = "/api/projects/{projectId}/file-transfers", consumes = "multipart/form-data")
    public Map<String, Object> send(@PathVariable long projectId, @RequestPart("file") MultipartFile file,
                                    @RequestParam long recipientId, @RequestParam(required = false) String note,
                                    Authentication auth) {
        User user = currentUser.requireOperational(auth);
        long id = service.sendToMember(projectId, recipientId, file, note, user);
        return Map.of("id", id, "status", "SENT");
    }

    @GetMapping("/api/projects/{projectId}/file-transfer-recipients")
    public List<FileAttachmentService.Recipient> recipients(@PathVariable long projectId, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        return service.recipients(projectId, user);
    }

    @GetMapping("/api/projects/{projectId}/file-transfers")
    public List<AttachmentView> inbox(@PathVariable long projectId, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        return service.inbox(projectId, user).stream().map(AttachmentView::of).toList();
    }

    @GetMapping("/api/attachments/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable long id, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        var file = service.download(id, user);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(file.contentType() != null ? MediaType.parseMediaType(file.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(file.data());
    }

    @DeleteMapping("/api/attachments/{id}")
    public Map<String, Object> delete(@PathVariable long id, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        service.delete(id, user);
        return Map.of("status", "DELETED");
    }
}
