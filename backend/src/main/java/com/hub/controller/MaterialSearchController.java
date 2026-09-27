package com.hub.controller;

import com.hub.model.MaterialAskResponse;
import com.hub.model.MaterialHit;
import com.hub.model.User;
import com.hub.repository.SearchLogRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.MaterialSearchService;
import com.hub.service.ProjectAccessService;
import com.hub.service.ProcessingJobService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/materials")
public class MaterialSearchController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final MaterialSearchService materials;
    private final SearchLogRepository searchLogs;
    private final ProcessingJobService jobs;

    public MaterialSearchController(CurrentUserService currentUser,
                                    ProjectAccessService projectAccess,
                                    MaterialSearchService materials,
                                    SearchLogRepository searchLogs,
                                    ProcessingJobService jobs) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.materials = materials;
        this.searchLogs = searchLogs;
        this.jobs = jobs;
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

    @PostMapping("/ask-job")
    public ResponseEntity<Map<String,Object>> askJob(@PathVariable long projectId,
                                                     @RequestBody AskRequest request,
                                                     Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.isBlank()) throw new IllegalArgumentException("질문을 입력해 주세요.");
        if (question.length() > 1000) throw new IllegalArgumentException("질문이 너무 깁니다.");
        searchLogs.log(projectId, user.id(), question);
        long jobId = jobs.queueMaterialAsk(projectId, question, user);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "PENDING"));
    }

    @PostMapping("/ask")
    public MaterialAskResponse ask(@PathVariable long projectId,
                                   @RequestBody AskRequest request,
                                   Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        String question = request == null ? "" : request.question();
        searchLogs.log(projectId, user.id(), question == null ? "" : question.trim());
        return materials.ask(projectId, question, user);
    }
}
