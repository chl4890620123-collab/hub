package com.hub.model;

/**
 * projectRole is ADMIN for global administrators and MEMBER for assigned project users. canConfirm is
 * always true for ADMIN (who bypasses every project-scoped check) and otherwise reflects this specific
 * project's own confirm-permission grant - see ProjectAccessService.requireConfirmPermission.
 */
public record Project(long id, String name, String description, String departmentName, String teamName,
                      long createdBy, String projectRole, boolean canConfirm) {
    public boolean isAdminView() { return "ADMIN".equals(projectRole); }
}
