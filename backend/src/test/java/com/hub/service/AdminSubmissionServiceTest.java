package com.hub.service;

import com.hub.model.User;
import com.hub.repository.AdminSubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminSubmissionServiceTest {
    @Test
    void memberCanSubmitUrlWithoutTodoAndAdminReceivesProjectInbox() {
        AdminSubmissionRepository submissions = mock(AdminSubmissionRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        AdminSubmissionService service = new AdminSubmissionService(submissions, access, storage);

        User member = member(11L);
        when(submissions.create(
                eq(9L), eq(11L), eq("광주 출장 보고"), eq("현장 확인"), eq("https://example.com/report"),
                isNull(), isNull(), isNull(), isNull()
        )).thenReturn(501L);

        long id = service.submit(
                9L, "광주 출장 보고", "현장 확인", "https://example.com/report", null, member
        );

        assertEquals(501L, id);
        verify(access).requireAccess(9L, member);
        verify(submissions).create(
                9L, 11L, "광주 출장 보고", "현장 확인", "https://example.com/report",
                null, null, null, null
        );
    }

    @Test
    void adminCannotSubmitIntoDecisionInboxAsWorker() {
        AdminSubmissionRepository submissions = mock(AdminSubmissionRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        AdminSubmissionService service = new AdminSubmissionService(submissions, access, storage);

        User admin = admin(99L);

        assertThrows(AccessDeniedException.class,
                () -> service.submit(9L, "관리자 제출", null, "https://example.com", null, admin));

        verify(submissions, never()).create(anyLong(), anyLong(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void memberSeesOnlyOwnSubmissionsWhileAdminSeesWholeProjectInbox() {
        AdminSubmissionRepository submissions = mock(AdminSubmissionRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        AdminSubmissionService service = new AdminSubmissionService(submissions, access, storage);

        User member = member(11L);
        User admin = admin(99L);
        AdminSubmissionRepository.Submission own = submission(501L, 9L, 11L);
        AdminSubmissionRepository.Submission other = submission(502L, 9L, 12L);

        when(submissions.listForSender(9L, 11L)).thenReturn(List.of(own));
        when(submissions.listForProject(9L)).thenReturn(List.of(other, own));

        assertEquals(List.of(own), service.list(9L, member));
        assertEquals(List.of(other, own), service.list(9L, admin));

        verify(submissions).listForSender(9L, 11L);
        verify(submissions).listForProject(9L);
    }

    @Test
    void submissionRequiresFileOrHttpUrl() {
        AdminSubmissionRepository submissions = mock(AdminSubmissionRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        AdminSubmissionService service = new AdminSubmissionService(submissions, access, storage);

        User member = member(11L);
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(9L, "출장 보고", null, null, null, member));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(9L, "출장 보고", null, "file:///tmp/report", null, member));

        verify(submissions, never()).create(anyLong(), anyLong(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadedSubmissionStoresFileMetadata() throws Exception {
        AdminSubmissionRepository submissions = mock(AdminSubmissionRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        FileStorageService storage = mock(FileStorageService.class);
        AdminSubmissionService service = new AdminSubmissionService(submissions, access, storage);

        User member = member(11L);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(4L);
        when(file.getOriginalFilename()).thenReturn("trip.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3, 4});
        when(storage.save(eq(9L), eq("trip.pdf"), any(byte[].class))).thenReturn("/trusted/trip.pdf");
        when(submissions.create(
                9L, 11L, "출장 증빙", null, null,
                "trip.pdf", "application/pdf", 4L, "/trusted/trip.pdf"
        )).thenReturn(503L);

        long id = service.submit(9L, "출장 증빙", null, null, file, member);

        assertEquals(503L, id);
        verify(storage).save(eq(9L), eq("trip.pdf"), any(byte[].class));
    }

    private static AdminSubmissionRepository.Submission submission(long id, long projectId, long senderId) {
        return new AdminSubmissionRepository.Submission(
                id, projectId, senderId, "자료", null, "https://example.com/" + id,
                null, null, null, null, LocalDateTime.now()
        );
    }

    private static User member(long id) {
        return new User(id, "member" + id, "member" + id + "@example.test", "Member " + id,
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
    }

    private static User admin(long id) {
        return new User(id, "admin" + id, "admin" + id + "@example.test", "Admin " + id,
                "Hub", "Ops", "Admin", "Manager", "ADMIN", "ACTIVE", false, "APPROVED");
    }
}
