package com.hub.controller;

import com.hub.model.Project;
import com.hub.model.User;
import com.hub.repository.ProjectRepository;
import com.hub.service.CurrentUserService;
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

    public ProjectController(ProjectRepository projects,
                             CurrentUserService currentUser,
                             ProjectAccessService projectAccess) {
        this.projects = projects;
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
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
        if (!user.isAdmin()) throw new org.springframework.security.access.AccessDeniedException("Administrator permission required");
        String name = request.name().trim();
        String description = request.description() == null ? null : request.description().trim();
        long id = projects.create(name, description, user.id());
        return new Project(id, name, description, user.id(), "ADMIN");
    }

    @GetMapping("/{projectId}/members")
    public List<Map<String, Object>> members(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return projects.listMembers(projectId);
    }
}
