package com.hub.model;

/**
 * projectRole is ADMIN for administrators and MEMBER for assigned project users.
 * canConfirm is kept in the wire model for compatibility, but decision authority is ADMIN-only.
 */
public record Project(long id, String name, String description, String departmentName, String teamName,
                      long createdBy, String projectRole, boolean canConfirm) {
    public boolean isAdminView() { return "ADMIN".equals(projectRole); }
}
