// approved-account read model. Account status is the single source of truth for login eligibility.
package com.hub.model;

/**
 * Global authorization is deliberately limited to ADMIN/MEMBER.
 * Company/department/team/jobTitle are profile and approval context only and never grant permission.
 */
public record User(
        long id,
        String loginId,
        String email,
        String displayName,
        String companyName,
        String departmentName,
        String teamName,
        String jobTitle,
        String globalRole,
        String accountStatus,
        boolean mustChangePassword,
        String approvalStatus
) {
    public boolean isAdmin() { return "ADMIN".equals(globalRole); }
    public boolean isApproved() { return "APPROVED".equals(approvalStatus); }
    public boolean active() { return "ACTIVE".equals(accountStatus); }
    public boolean withdrawn() { return "WITHDRAWN".equals(accountStatus); }
}
