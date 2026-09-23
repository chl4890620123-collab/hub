// account repository. Effective role, requested role, and lifecycle status are separate.
// Physical user deletion is intentionally unsupported so historical TODO/evidence/audit references stay valid.
package com.hub.repository;

import com.hub.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record AuthUser(
            long id, String loginId, String email, String passwordHash, String displayName,
            String companyName, String departmentName, String teamName, String jobTitle,
            String globalRole, String requestedRole,
            String accountStatus, boolean mustChangePassword, String approvalStatus, String rejectionReason,
            int failedLoginCount, Instant lockedUntil, long authVersion
    ) {
        public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }
        public boolean active() { return "ACTIVE".equals(accountStatus); }
        public User asUser() {
            return new User(id, loginId, email, displayName, companyName, departmentName, teamName, jobTitle,
                    globalRole, accountStatus, mustChangePassword, approvalStatus);
        }
    }

    /** ADMIN review row; requestedRole is visible, password data never is. */
    public record SignupApplication(
            long id, String loginId, String email, String displayName, String companyName,
            String departmentName, String teamName, String jobTitle, String signupNote,
            Long requestedProjectId, String requestedProjectName,
            String requestedRole, String approvalStatus, String rejectionReason, Instant createdAt
    ) {}

    public long countActiveAdmins() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE account_status='ACTIVE' AND approval_status='APPROVED' AND global_role='ADMIN'", Long.class);
        return count == null ? 0 : count;
    }

    public long countApprovedAdmins() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE approval_status='APPROVED' AND global_role='ADMIN'", Long.class);
        return count == null ? 0 : count;
    }

    /** Serializes the first-admin claim. Must be called inside a transaction. */
    public void lockFirstAdminGuard() {
        jdbc.queryForObject("SELECT guard_value FROM system_guard WHERE guard_key='FIRST_ADMIN' FOR UPDATE", String.class);
    }

    public List<User> list() {
        return jdbc.query(userSelect() + " WHERE approval_status='APPROVED' ORDER BY CASE WHEN global_role='ADMIN' THEN 0 ELSE 1 END, display_name, id", (rs,n)->mapUser(rs));
    }

    public List<SignupApplication> listPendingApplications() {
        return jdbc.query("""
                SELECT u.id,u.login_id,u.email,u.display_name,u.company_name,u.department_name,u.team_name,u.job_title,u.signup_note,
                       u.requested_project_id,p.name AS requested_project_name,
                       u.requested_role,u.approval_status,u.rejection_reason,u.created_at
                FROM app_user u LEFT JOIN project p ON p.id=u.requested_project_id
                WHERE u.approval_status='PENDING' ORDER BY u.created_at ASC,u.id ASC
                """, (rs,n)->{
            long requestedProjectId = rs.getLong("requested_project_id");
            return new SignupApplication(
                rs.getLong("id"), rs.getString("login_id"), rs.getString("email"), rs.getString("display_name"),
                rs.getString("company_name"), rs.getString("department_name"), rs.getString("team_name"),
                rs.getString("job_title"), rs.getString("signup_note"),
                rs.wasNull() ? null : requestedProjectId, rs.getString("requested_project_name"),
                rs.getString("requested_role"), rs.getString("approval_status"), rs.getString("rejection_reason"),
                rs.getTimestamp("created_at").toInstant());
        });
    }

    public Optional<User> findById(long id) { return findAuthById(id).map(AuthUser::asUser); }

    /** Login accepts either normalized login id or email. */
    public Optional<AuthUser> findAuthByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String value = identifier.trim().toLowerCase();
        List<AuthUser> rows = jdbc.query(authSelect() + " WHERE lower(login_id)=? OR lower(email)=? LIMIT 1",
                (rs,n)->mapAuth(rs), value, value);
        return rows.stream().findFirst();
    }

    public Optional<AuthUser> findAuthByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        List<AuthUser> rows = jdbc.query(authSelect() + " WHERE lower(email)=lower(?) LIMIT 1",
                (rs,n)->mapAuth(rs), email.trim());
        return rows.stream().findFirst();
    }

    public Optional<AuthUser> findAuthById(long id) {
        List<AuthUser> rows = jdbc.query(authSelect() + " WHERE id=?", (rs,n)->mapAuth(rs), id);
        return rows.stream().findFirst();
    }

    public void clearExpiredLockByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return;
        String value = identifier.trim().toLowerCase();
        jdbc.update("""
                UPDATE app_user SET failed_login_count=0,locked_until=NULL
                WHERE (lower(login_id)=? OR lower(email)=?)
                  AND locked_until IS NOT NULL AND locked_until<=CURRENT_TIMESTAMP
                """, value, value);
    }

    public void recordFailedLogin(long userId, int maxAttempts, Duration lockDuration) {
        AuthUser user = findAuthById(userId).orElse(null);
        if (user == null) return;
        int next = user.failedLoginCount() + 1;
        if (next >= Math.max(1, maxAttempts)) {
            Duration duration = lockDuration == null ? Duration.ofMinutes(15) : lockDuration;
            jdbc.update("UPDATE app_user SET failed_login_count=?,locked_until=? WHERE id=?",
                    next, Timestamp.from(Instant.now().plus(duration)), userId);
        } else {
            jdbc.update("UPDATE app_user SET failed_login_count=? WHERE id=?", next, userId);
        }
    }

    public void recordSuccessfulLogin(long userId) {
        jdbc.update("UPDATE app_user SET failed_login_count=0,locked_until=NULL,last_login_at=CURRENT_TIMESTAMP WHERE id=?", userId);
    }

    /** Pending signup never receives its requested ADMIN permission before approval. */
    public long createSignup(String loginId, String email, String passwordHash, String displayName,
                             String companyName, String departmentName, String teamName, String jobTitle,
                             String signupNote, Long requestedProjectId, String requestedRole) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_user(login_id,email,password_hash,display_name,company_name,department_name,team_name,job_title,
                                         signup_note,requested_project_id,global_role,requested_role,account_status,must_change_password,approval_status,privacy_consent_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'MEMBER',?,'SUSPENDED',FALSE,'PENDING',CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            setCommonIdentity(ps, loginId, email, passwordHash, displayName, companyName, departmentName, teamName);
            ps.setString(8, trimNullable(jobTitle));
            ps.setString(9, trimNullable(signupNote));
            if (requestedProjectId == null) ps.setNull(10, java.sql.Types.BIGINT); else ps.setLong(10, requestedProjectId);
            ps.setString(11, requestedRole);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Signup id was not generated");
        return key.getKey().longValue();
    }

    /**
     * Bootstrap-only: creates the one admin an install gets when it has zero admins yet, either through
     * BootstrapService (operator-supplied password, mustChangePassword always true since that value may
     * have passed through a shell/CI log) or through SignupService.registerAdmin's first-admin path (the
     * applicant's own freshly-chosen password, mustChangePassword false - they already know it). Every
     * other admin account is created through the normal signup/approve path instead.
     */
    public long createBootstrapAdmin(String loginId, String email, String passwordHash, String displayName, boolean mustChangePassword) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_user(login_id,email,password_hash,display_name,
                                         global_role,requested_role,account_status,must_change_password,approval_status,approved_at,privacy_consent_at)
                    VALUES(?,?,?,?,'ADMIN','ADMIN','ACTIVE',?,'APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setString(1, loginId); ps.setString(2, email); ps.setString(3, passwordHash); ps.setString(4, displayName);
            ps.setBoolean(5, mustChangePassword);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Admin id was not generated");
        return key.getKey().longValue();
    }

    /** Rejected applicants can reuse the row only for the same requested role. */
    public boolean reopenRejectedSignup(long userId, String passwordHash, String displayName, String companyName,
                                        String departmentName, String teamName, String jobTitle, String signupNote,
                                        Long requestedProjectId, String requestedRole) {
        return jdbc.update("""
                UPDATE app_user SET password_hash=?,display_name=?,company_name=?,department_name=?,team_name=?,job_title=?,signup_note=?,
                    requested_project_id=?,
                    approval_status='PENDING',account_status='SUSPENDED',rejection_reason=NULL,rejected_at=NULL,approved_by=NULL,approved_at=NULL,
                    failed_login_count=0,locked_until=NULL,auth_version=auth_version+1,privacy_consent_at=CURRENT_TIMESTAMP
                WHERE id=? AND approval_status='REJECTED' AND requested_role=?
                """, passwordHash, displayName.trim(), trimNullable(companyName), trimNullable(departmentName), trimNullable(teamName),
                trimNullable(jobTitle), trimNullable(signupNote), requestedProjectId, userId, requestedRole) == 1;
    }

    /** Approval activates exactly the requested role. */
    public boolean approveSignup(long userId, long adminId) {
        return jdbc.update("""
                UPDATE app_user SET approval_status='APPROVED',account_status='ACTIVE',approved_by=?,approved_at=CURRENT_TIMESTAMP,
                    rejected_at=NULL,rejection_reason=NULL,global_role=requested_role,auth_version=auth_version+1
                WHERE id=? AND approval_status='PENDING'
                """, adminId, userId) == 1;
    }

    public boolean rejectSignup(long userId, String reason) {
        return jdbc.update("""
                UPDATE app_user SET approval_status='REJECTED',account_status='SUSPENDED',rejected_at=CURRENT_TIMESTAMP,
                    rejection_reason=?,auth_version=auth_version+1
                WHERE id=? AND approval_status='PENDING'
                """, trimNullable(reason), userId) == 1;
    }

    public void updateProfile(long userId, String departmentName, String teamName, String jobTitle) {
        jdbc.update("UPDATE app_user SET department_name=?,team_name=?,job_title=? WHERE id=? AND approval_status='APPROVED'",
                trimNullable(departmentName), trimNullable(teamName), trimNullable(jobTitle), userId);
    }

    public void updatePassword(long userId, String passwordHash) {
        jdbc.update("""
                UPDATE app_user SET password_hash=?,password_changed_at=CURRENT_TIMESTAMP,must_change_password=FALSE,
                    failed_login_count=0,locked_until=NULL,auth_version=auth_version+1 WHERE id=?
                """, passwordHash, userId);
    }

    public void resetPassword(long userId, String passwordHash) {
        jdbc.update("""
                UPDATE app_user SET password_hash=?,password_changed_at=CURRENT_TIMESTAMP,must_change_password=TRUE,
                    failed_login_count=0,locked_until=NULL,auth_version=auth_version+1 WHERE id=?
                """, passwordHash, userId);
    }

    /** Account lifecycle is explicit; callers never physically delete a user row. */
    public boolean setAccountStatus(long userId, String status, String reason) {
        return jdbc.update("""
                UPDATE app_user SET account_status=?,status_reason=?,
                    suspended_at=CASE WHEN ?='SUSPENDED' THEN CURRENT_TIMESTAMP ELSE suspended_at END,
                    withdrawn_at=CASE WHEN ?='WITHDRAWN' THEN CURRENT_TIMESTAMP ELSE withdrawn_at END,
                    auth_version=auth_version+1
                WHERE id=? AND approval_status='APPROVED'
                """, status, trimNullable(reason), status, status, userId) == 1;
    }

    public void setRole(long userId, String role) {
        jdbc.update("UPDATE app_user SET global_role=?,requested_role=?,auth_version=auth_version+1 WHERE id=? AND approval_status='APPROVED'", role, role, userId);
    }

    private String userSelect() {
        return "SELECT id,login_id,email,display_name,company_name,department_name,team_name,job_title,global_role,account_status,must_change_password,approval_status FROM app_user";
    }

    private String authSelect() {
        return "SELECT id,login_id,email,password_hash,display_name,company_name,department_name,team_name,job_title,global_role,requested_role,account_status," +
                "must_change_password,approval_status,rejection_reason,failed_login_count,locked_until,auth_version FROM app_user";
    }

    private AuthUser mapAuth(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp locked = rs.getTimestamp("locked_until");
        return new AuthUser(rs.getLong("id"), rs.getString("login_id"), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("display_name"), rs.getString("company_name"),
                rs.getString("department_name"), rs.getString("team_name"), rs.getString("job_title"),
                rs.getString("global_role"), rs.getString("requested_role"), rs.getString("account_status"),
                rs.getBoolean("must_change_password"), rs.getString("approval_status"), rs.getString("rejection_reason"),
                rs.getInt("failed_login_count"), locked == null ? null : locked.toInstant(), rs.getLong("auth_version"));
    }

    private User mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new User(rs.getLong("id"), rs.getString("login_id"), rs.getString("email"),
                rs.getString("display_name"), rs.getString("company_name"), rs.getString("department_name"),
                rs.getString("team_name"), rs.getString("job_title"), rs.getString("global_role"), rs.getString("account_status"),
                rs.getBoolean("must_change_password"), rs.getString("approval_status"));
    }

    private static String normalizeLoginId(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase();
    }
    private static String trimNullable(String value) {
        if (value == null) return null;
        String v=value.trim();
        return v.isBlank()?null:v;
    }
    private static void setCommonIdentity(PreparedStatement ps,
                                          String loginId,
                                          String email,
                                          String passwordHash,
                                          String displayName,
                                          String companyName,
                                          String departmentName,
                                          String teamName) throws java.sql.SQLException {
        ps.setString(1, normalizeLoginId(loginId));
        ps.setString(2, email.trim().toLowerCase());
        ps.setString(3, passwordHash);
        ps.setString(4, displayName.trim());
        ps.setString(5, trimNullable(companyName));
        ps.setString(6, trimNullable(departmentName));
        ps.setString(7, trimNullable(teamName));
    }

}
