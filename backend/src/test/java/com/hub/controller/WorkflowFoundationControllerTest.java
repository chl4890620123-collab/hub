package com.hub.controller;

import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import com.hub.service.WorkflowFoundationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkflowFoundationControllerTest {
    @Test
    void inaccessibleProjectIsRejectedBeforeReadingWorkflow() {
        CurrentUserService current = mock(CurrentUserService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        WorkflowFoundationService workflow = mock(WorkflowFoundationService.class);
        Authentication auth = mock(Authentication.class);
        User member = new User(5, "member", null, "담당자", null, null, null, null,
                "MEMBER", "ACTIVE", false, "APPROVED");
        when(current.requireOperational(auth)).thenReturn(member);
        doThrow(new AccessDeniedException("프로젝트 권한 없음")).when(access).requireAccess(2, member);

        assertThrows(AccessDeniedException.class,
                () -> new WorkflowFoundationController(current, access, workflow).todo(2, 7, auth));
        verifyNoInteractions(workflow);
    }
}
