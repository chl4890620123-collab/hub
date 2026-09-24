package com.hub.controller;

import com.hub.model.MaterialAskResponse;
import com.hub.model.MaterialHit;
import com.hub.model.User;
import com.hub.repository.SearchLogRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.MaterialSearchService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/materials")
public class MaterialSearchController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final MaterialSearchService materials;
    private final SearchLogRepository searchLogs;

    public MaterialSearchController(CurrentUserService currentUser,
                                    ProjectAccessService projectAccess,
                                    MaterialSearchService materials,
                                    SearchLogRepository searchLogs) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.materials = materials;
        this.searchLogs = searchLogs;
    }

    @GetMapping("/search")
    public List<MaterialHit> search(@PathVariable long projectId,
                                    @RequestParam String q,
                                    @RequestParam(defaultValue = "0") int offset,
                                    Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        // Only log the query once per search, not on every "더보기" page.
        if (offset <= 0) searchLogs.log(projectId, user.id(), q == null ? "" : q.trim());
        return materials.search(projectId, q, offset, user);
    }

    public record AskRequest(String question) {}

    @PostMapping("/ask")
    public MaterialAskResponse ask(@PathVariable long projectId,
                                   @RequestBody AskRequest request,
                                   Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        String question = request == null ? "" : request.question();
        searchLogs.log(projectId, user.id(), question == null ? "" : question.trim());
        return materials.ask(projectId, question);
    }
}
