package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.RevisionRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class HistoryController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final RevisionRepository revisions;

    public HistoryController(CurrentUserService current, ProjectAccessService access, RevisionRepository revisions) {
        this.current = current;
        this.access = access;
        this.revisions = revisions;
    }

    @GetMapping("/api/projects/{projectId}/revisions")
    public List<Map<String,Object>> revisions(@PathVariable long projectId, Authentication auth) {
        User user = current.requireOperational(auth);
        access.requireAdmin(projectId, user);
        return revisions.list(projectId, 200);
    }
}
