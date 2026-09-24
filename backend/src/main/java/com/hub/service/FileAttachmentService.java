package com.hub.service;

import com.hub.model.User;
import com.hub.repository.FileAttachmentRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.TodoRepository;
import com.hub.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Attaching a file to a todo, or sending one straight to a teammate. Both share the same storage and
 * metadata table; only who can see the row differs (todo project members vs. sender/recipient).
 */
@Service
public class FileAttachmentService {
    private static final long MAX_FILE_BYTES = 100L * 1024L * 1024L;

    private final FileAttachmentRepository attachments;
    private final TodoRepository todos;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final ProjectAccessService access;
    private final FileStorageService storage;

    public FileAttachmentService(FileAttachmentRepository attachments, TodoRepository todos, ProjectRepository projects,
                                 UserRepository users, ProjectAccessService access, FileStorageService storage) {
        this.attachments = attachments; this.todos = todos; this.projects = projects;
        this.users = users; this.access = access; this.storage = storage;
    }

    public long attachToTodo(long todoId, MultipartFile file, String note, User actor) {
        var todo = todos.find(todoId);
        access.requireAccess(todo.projectId(), actor);
        String path = save(todo.projectId(), file);
        return attachments.create(todo.projectId(), todoId, actor.id(), null,
                safeName(file), file.getContentType(), file.getSize(), path, blankToNull(note));
    }

    public record Recipient(long id, String displayName, String loginId, boolean admin) {}

    public List<Recipient> recipients(long projectId, User actor) {
        access.requireAccess(projectId, actor);
        LinkedHashMap<Long, Recipient> result = new LinkedHashMap<>();
        for (var row : projects.listMembers(projectId)) {
            Object idValue = value(row, "user_id");
            if (!(idValue instanceof Number id)) continue;
            result.put(id.longValue(), new Recipient(
                    id.longValue(),
                    String.valueOf(value(row, "display_name")),
                    String.valueOf(value(row, "login_id")),
                    false
            ));
        }
        for (User admin : users.list()) {
            if (!admin.isAdmin() || !admin.active()) continue;
            result.put(admin.id(), new Recipient(admin.id(), admin.displayName(), admin.loginId(), true));
        }
        return result.values().stream()
                .sorted(Comparator.comparing(Recipient::admin).reversed()
                        .thenComparing(Recipient::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(Recipient::id))
                .toList();
    }

    public long sendToMember(long projectId, long recipientId, MultipartFile file, String note, User actor) {
        access.requireAccess(projectId, actor);
        boolean recipientOk = recipientId == actor.id()
                || projects.isMember(projectId, recipientId)
                || users.findById(recipientId).map(User::isAdmin).orElse(false);
        if (!recipientOk) throw new IllegalArgumentException("받는 사람은 같은 프로젝트 팀원 또는 관리자여야 합니다.");
        String path = save(projectId, file);
        return attachments.create(projectId, null, actor.id(), recipientId,
                safeName(file), file.getContentType(), file.getSize(), path, blankToNull(note));
    }

    public List<FileAttachmentRepository.Attachment> listForTodo(long todoId, User actor) {
        var todo = todos.find(todoId);
        access.requireAccess(todo.projectId(), actor);
        return attachments.listForTodo(todoId);
    }

    public List<FileAttachmentRepository.Attachment> inbox(long projectId, User actor) {
        access.requireAccess(projectId, actor);
        return attachments.listForUser(projectId, actor.id());
    }

    public record Download(byte[] data, String fileName, String contentType) {}

    public Download download(long id, User actor) {
        var attachment = attachments.find(id).orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));
        requireCanSee(attachment, actor);
        if (attachment.recipientId() != null && attachment.recipientId() == actor.id()) attachments.markRead(id);
        byte[] data = storage.readTrusted(attachment.storagePath());
        return new Download(data, attachment.fileName(), attachment.contentType());
    }

    public void delete(long id, User actor) {
        var attachment = attachments.find(id).orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));
        // Sender ownership alone is not enough after the sender leaves the project. Download already
        // enforces current project access; deletion must use the same boundary.
        access.requireAccess(attachment.projectId(), actor);
        boolean owner = attachment.senderId() == actor.id();
        if (!owner && !access.isAdmin(attachment.projectId(), actor))
            throw new AccessDeniedException("보낸 사람이나 관리자만 지울 수 있습니다.");
        storage.deleteStrict(attachment.storagePath());
        if (!attachments.delete(id)) throw new IllegalArgumentException("삭제할 파일을 찾을 수 없습니다.");
    }

    private void requireCanSee(FileAttachmentRepository.Attachment attachment, User actor) {
        access.requireAccess(attachment.projectId(), actor);
        boolean direct = attachment.senderId() == actor.id()
                || (attachment.recipientId() != null && attachment.recipientId() == actor.id());
        boolean todoAttachment = attachment.todoId() != null;
        if (!direct && !todoAttachment && !actor.isAdmin())
            throw new AccessDeniedException("이 파일을 볼 수 있는 권한이 없습니다.");
    }

    private String save(long projectId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("빈 파일은 보낼 수 없습니다.");
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("파일은 100MB 이하로 보내 주세요.");
        try {
            return storage.save(projectId, safeName(file), file.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("파일을 읽는 데 실패했습니다.", e);
        }
    }

    private static String safeName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "attachment.bin" : java.nio.file.Path.of(name).getFileName().toString();
    }

    private static Object value(java.util.Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(java.util.Locale.ROOT));
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
