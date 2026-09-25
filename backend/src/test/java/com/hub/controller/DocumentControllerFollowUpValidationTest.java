package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.DocumentRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.DocumentService;
import com.hub.service.ProcessingJobService;
import com.hub.service.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentControllerFollowUpValidationTest {
    @Test
    void uploadStoresDocumentWithoutStartingAiUntilConfirmed() {
        CurrentUserService current = mock(CurrentUserService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        DocumentService documents = mock(DocumentService.class);
        ProcessingJobService jobs = mock(ProcessingJobService.class);
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentController controller = new DocumentController(current, access, documents, jobs, repository);

        Authentication auth = mock(Authentication.class);
        MultipartFile file = mock(MultipartFile.class);
        User user = member(7L);
        when(current.requireOperational(auth)).thenReturn(user);
        when(documents.upload(10L, file, user)).thenReturn(21L);
        when(repository.documentIdForVersion(21L)).thenReturn(12L);

        Map<String, Object> result = controller.upload(10L, file, auth);

        verify(access).requireAccess(10L, user);
        verify(documents).upload(10L, file, user);
        verify(jobs, never()).queueDocument(any(Long.class), any(Long.class), any());
        assertEquals("UPLOADED", result.get("status"));
        assertEquals(21L, result.get("versionId"));
        assertEquals(12L, result.get("documentId"));
    }

    private static User member(long id) {
        return new User(id, "member" + id, "member" + id + "@example.test", "Member " + id,
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
    }
}
