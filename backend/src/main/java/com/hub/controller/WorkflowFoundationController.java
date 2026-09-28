package com.hub.controller;

import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import com.hub.service.WorkflowFoundationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkflowFoundationController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final WorkflowFoundationService workflow;

    public WorkflowFoundationController(CurrentUserService current, ProjectAccessService access,
                                        WorkflowFoundationService workflow) {
        this.current = current;
        this.access = access;
        this.workflow = workflow;
    }

    @GetMapping("/api/projects/{projectId}/workflow/todos/{todoId}")
    public WorkflowFoundationService.Snapshot todo(@PathVariable long projectId, @PathVariable long todoId,
                                                   Authentication authentication) {
        User user = current.requireOperational(authentication);
        access.requireAccess(projectId, user);
        return workflow.todo(projectId, todoId);
    }
}
