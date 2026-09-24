package com.hub.controller;

import com.hub.model.Project;
import com.hub.model.User;
import com.hub.repository.ProjectRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.MembershipService;
import com.hub.service.ProjectAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectRepository projects;
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final MembershipService memberships;

    public ProjectController(ProjectRepository projects,
                             CurrentUserService currentUser,
                             ProjectAccessService projectAccess,
                             MembershipService memberships) {
        this.projects = projects;
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.memberships = memberships;
    }

    public record CreateProject(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description
    ) {
    }

    @GetMapping
    public List<Project> list(Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        return projects.listForUser(user.id(), user.isAdmin());
    }

    @PostMapping
    public Project create(@Valid @RequestBody CreateProject request, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        if (!user.isAdmin()) throw new org.springframework.security.access.AccessDeniedException("관리자 권한이 필요합니다.");
        String name = request.name().trim();
        String description = request.description() == null ? null : request.description().trim();
        long id = projects.create(name, description, user.id());
        return new Project(id, name, description, user.id(), "ADMIN", true);
    }

    @GetMapping("/{projectId}/members")
    public List<Map<String, Object>> members(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return projects.listMembers(projectId);
    }

    /** Decision-maker self-service: who's left to invite into this project. Company ADMIN already
     * has a broader version of this on /api/admin/projects/{id}/members - this is the narrower,
     * project-scoped one a non-admin decision-maker can reach. */
    @GetMapping("/{projectId}/addable-users")
    public List<Map<String, Object>> addableUsers(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireConfirmPermission(projectId, user);
        return projects.listAddableUsers(projectId);
    }

    public record AddMember(long userId) {}

    @PostMapping("/{projectId}/members")
    public Map<String, Object> addMember(@PathVariable long projectId, @RequestBody AddMember body, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireConfirmPermission(projectId, user);
        memberships.add(projectId, body.userId(), user, "DECISION_MAKER_ADD");
        return Map.of("status", "ADDED");
    }

    @org.springframework.web.bind.annotation.PutMapping("/{projectId}")
    public Map<String, Object> rename(@PathVariable long projectId,
                                      @Valid @RequestBody CreateProject request,
                                      Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAdmin(projectId, user);
        if (!projects.exists(projectId)) throw new IllegalArgumentException("존재하지 않는 프로젝트입니다.");
        String name = request.name().trim();
        String description = request.description() == null ? null : request.description().trim();
        projects.rename(projectId, name, description);
        return Map.of("status", "UPDATED");
    }
}
