package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.SearchLogRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Search-history endpoints. Unified search and RAG live only under /materials to avoid duplicate APIs. */
@RestController
@RequestMapping("/api/projects/{projectId}/search")
public class SearchController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final SearchLogRepository searchLogs;

    public SearchController(CurrentUserService currentUser,
                            ProjectAccessService projectAccess,
                            SearchLogRepository searchLogs) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.searchLogs = searchLogs;
    }

    @GetMapping("/top")
    public List<Map<String, Object>> top(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return searchLogs.topForUser(projectId, user.id());
    }
}
