package com.hub.service;

import com.hub.model.TodoItem;
import com.hub.model.User;
import com.hub.repository.FileAttachmentRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.TodoRepository;
import com.hub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileAttachmentServiceVisibilityTest {
    @Test
    void todoAttachmentIsAddressedToCurrentAssignee() throws Exception {
        FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        TodoRepository todos = mock(TodoRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        FileAttachmentService service = new FileAttachmentService(attachments, todos, projects, users, access, storage);

        User sender = member(11L);
        when(todos.find(77L)).thenReturn(todo(77L, 9L, 22L, "CONFIRMED"));

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(4L);
        when(file.getOriginalFilename()).thenReturn("brief.txt");
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getBytes()).thenReturn(new byte[]{1,2,3,4});
        when(storage.save(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq("brief.txt"),
                org.mockito.ArgumentMatchers.any(byte[].class))).thenReturn("/trusted/brief.txt");
        when(attachments.create(9L, 77L, 11L, 22L, "brief.txt", "text/plain", 4L, "/trusted/brief.txt", null))
                .thenReturn(501L);

        long id = service.attachToTodo(77L, file, null, sender);

        assertEquals(501L, id);
        verify(attachments).create(9L, 77L, 11L, 22L, "brief.txt", "text/plain", 4L, "/trusted/brief.txt", null);
    }

    @Test
    void unassignedTodoCannotReceivePrivateAttachment() {
        FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        TodoRepository todos = mock(TodoRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        FileAttachmentService service = new FileAttachmentService(attachments, todos, projects, users, access, storage);

        User sender = member(11L);
        when(todos.find(77L)).thenReturn(todo(77L, 9L, null, "CONFIRMED"));

        assertThrows(IllegalArgumentException.class,
                () -> service.attachToTodo(77L, mock(MultipartFile.class), null, sender));

        verify(storage, never()).save(anyLong(), anyString(), any(byte[].class));
        verify(attachments, never()).create(anyLong(), any(), anyLong(), any(), anyString(), any(), anyLong(), anyString(), any());
    }

    @Test
    void administratorWhoIsNotSenderOrRecipientCannotDownloadPrivateFile() {
        FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        TodoRepository todos = mock(TodoRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        FileAttachmentService service = new FileAttachmentService(attachments, todos, projects, users, access, storage);

        User admin = new User(99L, "admin99", "admin99@example.test", "Admin",
                "Hub", null, null, null, "ADMIN", "ACTIVE", false, "APPROVED");
        FileAttachmentRepository.Attachment attachment = new FileAttachmentRepository.Attachment(
                501L, 9L, 77L, 11L, 22L, "brief.txt", "text/plain", 4L,
                "/trusted/brief.txt", null, null, LocalDateTime.now());
        when(attachments.find(501L)).thenReturn(Optional.of(attachment));

        assertThrows(AccessDeniedException.class, () -> service.download(501L, admin));

        verify(storage, never()).readTrusted(anyString());
    }

    private static TodoItem todo(long id, long projectId, Long assigneeId, String reviewStatus) {
        LocalDateTime now = LocalDateTime.now();
        return new TodoItem(id, projectId, "업무", null, assigneeId, assigneeId == null ? null : "담당자",
                null, null, null, null, "HIGH", reviewStatus, "TODO", "ACTIVE",
                null, null, now, now, null, false, null, null, null);
    }

    private static User member(long id) {
        return new User(id, "member" + id, "member" + id + "@example.test", "Member " + id,
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
    }
}
