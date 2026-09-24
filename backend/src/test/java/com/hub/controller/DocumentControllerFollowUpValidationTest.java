package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.DocumentRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.DocumentService;
import com.hub.service.ProcessingJobService;
import com.hub.service.ProjectAccessService;
import com.hub.service.TodoService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentControllerFollowUpValidationTest {
    @Test
    void uploadWithDueDateButNoAssigneeFailsBeforeDocumentIsStored() {
        CurrentUserService current = mock(CurrentUserService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        DocumentService documents = mock(DocumentService.class);
        ProcessingJobService jobs = mock(ProcessingJobService.class);
        DocumentRepository repository = mock(DocumentRepository.class);
        TodoService todos = mock(TodoService.class);
        DocumentController controller = new DocumentController(current, access, documents, jobs, repository, todos);

        Authentication auth = mock(Authentication.class);
        MultipartFile file = mock(MultipartFile.class);
        User user = member(7L);
        when(current.requireOperational(auth)).thenReturn(user);

        assertThrows(IllegalArgumentException.class, () ->
                controller.upload(10L, file, null, LocalDate.of(2026, 10, 1), null, auth));

        verify(access).requireAccess(10L, user);
        verify(documents, never()).upload(anyLong(), any(MultipartFile.class), any(User.class));
        verify(jobs, never()).queueDocument(anyLong(), anyLong(), any());
    }

    @Test
    void manualEntryWithAssigneeButNoDueDateFailsBeforeDocumentIsStored() {
        CurrentUserService current = mock(CurrentUserService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        DocumentService documents = mock(DocumentService.class);
        ProcessingJobService jobs = mock(ProcessingJobService.class);
        DocumentRepository repository = mock(DocumentRepository.class);
        TodoService todos = mock(TodoService.class);
        DocumentController controller = new DocumentController(current, access, documents, jobs, repository, todos);

        Authentication auth = mock(Authentication.class);
        User user = member(7L);
        when(current.requireOperational(auth)).thenReturn(user);

        DocumentController.ManualText request =
                new DocumentController.ManualText("메모", "내용", null, null, 8L);

        assertThrows(IllegalArgumentException.class, () -> controller.manual(10L, request, auth));

        verify(access).requireAccess(10L, user);
        verify(documents, never()).manualText(anyLong(), any(), any(), any(User.class));
        verify(jobs, never()).queueDocument(anyLong(), anyLong(), any());
    }

    private static User member(long id) {
        return new User(id, "member" + id, "member" + id + "@example.test", "Member " + id,
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
    }
}
