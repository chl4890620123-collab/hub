package com.hub.service;

import com.hub.model.User;
import com.hub.repository.ProjectRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {
    private final ProjectRepository projects;
    public ProjectAccessService(ProjectRepository projects) { this.projects = projects; }

    public void requireAccess(long projectId, User user) {
        if (!projects.canAccess(projectId, user.id(), user.isAdmin())) {
            throw new AccessDeniedException("Project access denied");
        }
    }

    public boolean isAdmin(long projectId, User user) {
        return user != null && user.isAdmin();
    }

    public void requireAdmin(long projectId, User user) {
        requireAccess(projectId, user);
        if (user == null || !user.isAdmin()) {
            throw new AccessDeniedException("Administrator permission required");
        }
    }
}
