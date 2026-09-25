package com.hub.service;

import com.hub.model.User;
import com.hub.repository.FileAttachmentRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.TodoRepository;
import com.hub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileAttachmentServiceDeleteTest {
    private static long eqLong(long value) { return org.mockito.ArgumentMatchers.eq(value); }
    private static Long eqLongObj(Long value) { return org.mockito.ArgumentMatchers.eq(value); }
    @Test
    void senderWhoNoLongerHasProjectAccessCannotDeleteOldAttachment() {
        FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        TodoRepository todos = mock(TodoRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        FileAttachmentService service = new FileAttachmentService(attachments, todos, projects, users, access, storage);

        User sender = member(11L);
        FileAttachmentRepository.Attachment attachment = attachment(77L, 9L, sender.id());
        when(attachments.find(77L)).thenReturn(Optional.of(attachment));
        doThrow(new AccessDeniedException("no project access")).when(access).requireAccess(9L, sender);

        assertThrows(AccessDeniedException.class, () -> service.delete(77L, sender));

        verify(storage, never()).deleteStrict(anyString());
        verify(attachments, never()).delete(anyLong());
    }

    @Test
    void inactiveAdminCannotReceiveDirectFile() {
        FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        TodoRepository todos = mock(TodoRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        FileAttachmentService service = new FileAttachmentService(attachments, todos, projects, users, access, storage);

        User sender = member(11L);
        User inactiveAdmin = new User(99L, "admin99", "admin99@example.test", "Inactive Admin",
                "Hub", null, null, null, "ADMIN", "SUSPENDED", false, "APPROVED");
        when(projects.isMember(9L, 99L)).thenReturn(false);
        when(users.findById(99L)).thenReturn(Optional.of(inactiveAdmin));

        assertThrows(IllegalArgumentException.class,
                () -> service.sendToMember(9L, 99L, mock(org.springframework.web.multipart.MultipartFile.class), null, sender));

        verify(storage, never()).save(anyLong(), anyString(), org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void failedDirectTransferMetadataInsertRemovesStoredFile() throws Exception {
        FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        TodoRepository todos = mock(TodoRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        FileAttachmentService service = new FileAttachmentService(attachments, todos, projects, users, access, storage);

        User sender = member(11L);
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(projects.isMember(9L, 22L)).thenReturn(true);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(12L);
        when(file.getOriginalFilename()).thenReturn("file.txt");
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(storage.save(eqLong(9L), anyString(), any(byte[].class))).thenReturn("/trusted/orphan.txt");
        when(attachments.create(eqLong(9L), isNull(), eqLong(11L), eqLongObj(22L), anyString(), anyString(),
                eqLong(12L), anyString(), isNull())).thenThrow(new IllegalStateException("db failed"));

        assertThrows(IllegalStateException.class, () -> service.sendToMember(9L, 22L, file, null, sender));

        verify(storage).deleteQuietly("/trusted/orphan.txt");
    }

    @Test
    void authorizedSenderDeletesPhysicalFileBeforeMetadata() {
        FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        TodoRepository todos = mock(TodoRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        FileAttachmentService service = new FileAttachmentService(attachments, todos, projects, users, access, storage);

        User sender = member(11L);
        FileAttachmentRepository.Attachment attachment = attachment(77L, 9L, sender.id());
        when(attachments.find(77L)).thenReturn(Optional.of(attachment));
        when(attachments.delete(77L)).thenReturn(true);

        service.delete(77L, sender);

        InOrder order = inOrder(access, storage, attachments);
        order.verify(access).requireAccess(9L, sender);
        order.verify(storage).deleteStrict("/trusted/file.txt");
        order.verify(attachments).delete(77L);
    }

    private static FileAttachmentRepository.Attachment attachment(long id, long projectId, long senderId) {
        return new FileAttachmentRepository.Attachment(
                id, projectId, null, senderId, 22L, "file.txt", "text/plain", 12L,
                "/trusted/file.txt", null, null, LocalDateTime.now()
        );
    }

    private static User member(long id) {
        return new User(id, "member" + id, "member" + id + "@example.test", "Member " + id,
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
    }
}
