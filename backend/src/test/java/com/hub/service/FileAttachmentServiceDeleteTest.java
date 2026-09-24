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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileAttachmentServiceDeleteTest {
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
