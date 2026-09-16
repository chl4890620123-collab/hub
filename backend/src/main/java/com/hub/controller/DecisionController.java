// Decision confirmation needs project-scoped confirm permission (or global ADMIN) and is one-way,
// writing revision/timeline only after a real state change.
package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.DecisionRepository;
import com.hub.repository.EvidenceRepository;
import com.hub.repository.RevisionRepository;
import com.hub.repository.TimelineRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class DecisionController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final DecisionRepository decisions;
    private final EvidenceRepository evidence;
    private final RevisionRepository revisions;
    private final TimelineRepository timeline;

    public DecisionController(CurrentUserService currentUser,
                              ProjectAccessService projectAccess,
                              DecisionRepository decisions,
                              EvidenceRepository evidence,
                              RevisionRepository revisions,
                              TimelineRepository timeline) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.decisions = decisions;
        this.evidence = evidence;
        this.revisions = revisions;
        this.timeline = timeline;
    }

    @GetMapping("/api/projects/{projectId}/review/decisions")
    public List<Map<String, Object>> pending(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireConfirmPermission(projectId, user);
        return decisions.pending(projectId);
    }

    @GetMapping("/api/decisions/{id}/evidence")
    public List<EvidenceRepository.EvidenceView> evidence(@PathVariable long id,
                                                           Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = decisions.projectId(id);
        projectAccess.requireAccess(projectId, user);
        return evidence.forDecision(id);
    }

    @PostMapping("/api/decisions/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable long id, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = decisions.projectId(id);
        projectAccess.requireConfirmPermission(projectId, user);
        if (!decisions.confirm(id, user.id())) throw new com.hub.service.StateConflictException("이미 확정된 결정 후보입니다.");
        revisions.add(
                projectId,
                "DECISION",
                id,
                user.id(),
                "CONFIRM",
                null,
                "{\"reviewStatus\":\"CONFIRMED\"}"
        );
        timeline.append(
                projectId,
                "DECISION_CONFIRMED",
                "Decision confirmed",
                null,
                LocalDateTime.now(),
                "DECISION",
                id
        );
        return Map.of("status", "CONFIRMED");
    }

    @PostMapping("/api/decisions/{id}/reject")
    public Map<String, Object> reject(@PathVariable long id, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        long projectId = decisions.projectId(id);
        projectAccess.requireConfirmPermission(projectId, user);
        if (!decisions.reject(id, user.id())) throw new com.hub.service.StateConflictException("이미 처리된 결정 후보입니다.");
        revisions.add(projectId, "DECISION", id, user.id(), "REJECT", null, "{\"reviewStatus\":\"REJECTED\"}");
        return Map.of("status", "REJECTED");
    }
}
