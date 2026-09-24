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
            throw new AccessDeniedException("이 프로젝트를 볼 수 있는 권한이 없습니다.");
        }
    }

    public boolean isAdmin(long projectId, User user) {
        return user != null && user.isAdmin();
    }

    public void requireAdmin(long projectId, User user) {
        requireAccess(projectId, user);
        if (user == null || !user.isAdmin()) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }
    }

    /** Confirming AI-extracted todos: global ADMIN always passes; otherwise the project's own confirm grant. */
    public boolean canConfirm(long projectId, User user) {
        return user != null && (user.isAdmin() || projects.canConfirm(projectId, user.id()));
    }

    public void requireConfirmPermission(long projectId, User user) {
        requireAccess(projectId, user);
        if (!canConfirm(projectId, user)) {
            throw new AccessDeniedException("할 일을 확정할 수 있는 권한이 필요합니다.");
        }
    }
}
