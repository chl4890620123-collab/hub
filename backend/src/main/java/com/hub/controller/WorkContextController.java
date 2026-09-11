package com.hub.controller;

import com.hub.model.User;
import com.hub.model.WorkContextBundle;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import com.hub.service.WorkContextService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/context")
public class WorkContextController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final WorkContextService contexts;

    public WorkContextController(CurrentUserService current, ProjectAccessService access, WorkContextService contexts) {
        this.current = current;
        this.access = access;
        this.contexts = contexts;
    }

    @GetMapping
    public WorkContextBundle context(@PathVariable long projectId, @RequestParam String q, Authentication auth) {
        User user = current.requireOperational(auth);
        access.requireAccess(projectId, user);
        return contexts.build(projectId, q);
    }
}
