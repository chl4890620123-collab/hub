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
 * metadata table. Files addressed to a todo assignee or teammate are private to sender/recipient.
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
        if (!"CONFIRMED".equals(todo.reviewStatus()) || todo.assigneeId() == null) {
            throw new IllegalArgumentException("담당자가 지정된 확정 할 일에만 파일을 보낼 수 있습니다.");
        }
        String path = save(todo.projectId(), file);
        return attachments.create(todo.projectId(), todoId, actor.id(), todo.assigneeId(),
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
                || users.findById(recipientId).filter(User::active).map(User::isAdmin).orElse(false);
        if (!recipientOk) throw new IllegalArgumentException("받는 사람은 같은 프로젝트 팀원 또는 관리자여야 합니다.");
        String path = save(projectId, file);
        return attachments.create(projectId, null, actor.id(), recipientId,
                safeName(file), file.getContentType(), file.getSize(), path, blankToNull(note));
    }

    public List<FileAttachmentRepository.Attachment> listForTodo(long todoId, User actor) {
        var todo = todos.find(todoId);
        access.requireAccess(todo.projectId(), actor);
        return attachments.listForTodoVisible(todoId, actor.id());
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

    public void update(long id, String fileName, String note, User actor) {
        var attachment = attachments.find(id).orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));
        access.requireAccess(attachment.projectId(), actor);
        if (attachment.senderId() != actor.id())
            throw new AccessDeniedException("보낸 사람만 파일 이름과 메모를 수정할 수 있습니다.");
        String safeFileName = fileName == null || fileName.isBlank() ? attachment.fileName() : java.nio.file.Path.of(fileName.trim()).getFileName().toString();
        if (safeFileName.length() > 500) throw new IllegalArgumentException("파일 이름은 500자 이하여야 합니다.");
        String safeNote = blankToNull(note);
        if (safeNote != null && safeNote.length() > 1000) throw new IllegalArgumentException("메모는 1000자 이하여야 합니다.");
        if (!attachments.updateMetadata(id, safeFileName, safeNote))
            throw new IllegalArgumentException("수정할 파일을 찾을 수 없습니다.");
    }

    public void delete(long id, User actor) {
        var attachment = attachments.find(id).orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));
        // Sender ownership alone is not enough after the sender leaves the project. Download already
        // enforces current project access; deletion must use the same boundary.
        access.requireAccess(attachment.projectId(), actor);
        boolean owner = attachment.senderId() == actor.id();
        if (!owner)
            throw new AccessDeniedException("보낸 사람만 원본 파일을 지울 수 있습니다.");
        storage.deleteStrict(attachment.storagePath());
        if (!attachments.delete(id)) throw new IllegalArgumentException("삭제할 파일을 찾을 수 없습니다.");
    }

    public void removeFromInbox(long id, User actor) {
        var attachment = attachments.find(id).orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));
        access.requireAccess(attachment.projectId(), actor);
        if (attachment.todoId() != null || attachment.recipientId() == null || attachment.recipientId() != actor.id())
            throw new AccessDeniedException("받은 파일만 내 목록에서 숨길 수 있습니다.");
        if (!attachments.hideForRecipient(id, actor.id()))
            throw new IllegalArgumentException("이미 목록에서 숨긴 파일입니다.");
    }

    private void requireCanSee(FileAttachmentRepository.Attachment attachment, User actor) {
        access.requireAccess(attachment.projectId(), actor);
        boolean direct = attachment.senderId() == actor.id()
                || (attachment.recipientId() != null && attachment.recipientId() == actor.id());
        if (!direct) throw new AccessDeniedException("이 파일은 보낸 사람과 받는 담당자만 볼 수 있습니다.");
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
