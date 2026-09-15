// comparison remains available to members, while only ADMIN can commit a one-way confirmed change.
package com.hub.controller;

import com.hub.dto.AiDtos;
import com.hub.model.User;
import com.hub.repository.ChangeRepository;
import com.hub.repository.DocumentRepository;
import com.hub.repository.EvidenceRepository;
import com.hub.repository.RevisionRepository;
import com.hub.service.ChangeService;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/changes")
public class ChangeController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final ChangeService changeService;
    private final DocumentRepository documents;
    private final ChangeRepository changes;
    private final EvidenceRepository evidence;
    private final RevisionRepository revisions;

    public ChangeController(CurrentUserService currentUser,
                            ProjectAccessService projectAccess,
                            ChangeService changeService,
                            DocumentRepository documents,
                            ChangeRepository changes,
                            EvidenceRepository evidence,
                            RevisionRepository revisions) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.changeService = changeService;
        this.documents = documents;
        this.changes = changes;
        this.evidence = evidence;
        this.revisions = revisions;
    }

    public record Compare(long beforeVersionId, long afterVersionId) {
    }

    @PostMapping
    public AiDtos.ChangeResponse compare(@PathVariable long projectId,
                                          @RequestBody Compare request,
                                          Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        if (documents.projectIdForVersion(request.beforeVersionId()) != projectId
                || documents.projectIdForVersion(request.afterVersionId()) != projectId) {
            throw new IllegalArgumentException("비교할 두 자료는 모두 현재 프로젝트의 자료여야 합니다.");
        }
        return changeService.compare(
                projectId,
                request.beforeVersionId(),
                request.afterVersionId(),
                user
        );
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return changeService.list(projectId);
    }

    @GetMapping("/review")
    public List<Map<String, Object>> pending(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAdmin(projectId, user);
        return changes.pending(projectId);
    }

    @GetMapping("/items/{itemId}/evidence")
    public List<EvidenceRepository.ChangeEvidenceView> evidence(@PathVariable long projectId,
                                                                 @PathVariable long itemId,
                                                                 Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        if (changes.projectIdForItem(itemId) != projectId) {
            throw new IllegalArgumentException("현재 프로젝트의 변경 항목이 아닙니다.");
        }
        return evidence.forChange(itemId);
    }

    @PostMapping("/items/{itemId}/confirm")
    public Map<String, Object> confirm(@PathVariable long projectId,
                                       @PathVariable long itemId,
                                       Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAdmin(projectId, user);
        if (changes.projectIdForItem(itemId) != projectId) {
            throw new IllegalArgumentException("현재 프로젝트의 변경 항목이 아닙니다.");
        }
        if (!changes.confirmItem(itemId)) throw new com.hub.service.StateConflictException("이미 확정된 변경 후보입니다.");
        revisions.add(
                projectId,
                "CHANGE_ITEM",
                itemId,
                user.id(),
                "CONFIRM",
                null,
                "{\"reviewStatus\":\"CONFIRMED\"}"
        );
        return Map.of("status", "CONFIRMED");
    }

    @PostMapping("/items/{itemId}/reject")
    public Map<String, Object> reject(@PathVariable long projectId,
                                      @PathVariable long itemId,
                                      Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAdmin(projectId, user);
        if (changes.projectIdForItem(itemId) != projectId) {
            throw new IllegalArgumentException("현재 프로젝트의 변경 항목이 아닙니다.");
        }
        if (!changes.rejectItem(itemId)) throw new com.hub.service.StateConflictException("이미 처리된 변경 후보입니다.");
        revisions.add(projectId, "CHANGE_ITEM", itemId, user.id(), "REJECT", null, "{\"reviewStatus\":\"REJECTED\"}");
        return Map.of("status", "REJECTED");
    }
}
