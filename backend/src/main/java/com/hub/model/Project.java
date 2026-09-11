package com.hub.model;

/** projectRole is ADMIN for global administrators and MEMBER for assigned project users. */
public record Project(long id, String name, String description, long createdBy, String projectRole) {
    public boolean isAdminView() { return "ADMIN".equals(projectRole); }
}
