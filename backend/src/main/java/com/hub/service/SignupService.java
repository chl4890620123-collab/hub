// MEMBER/ADMIN signup paths share one validation pipeline; ADMIN signup collects only organization identity fields.
package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SignupService {
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-zA-Z0-9._-]{4,40}$");
    private static final int MAX_NAME = 100;
    private static final int MAX_COMPANY = 200;
    private static final int MAX_PROFILE = 200;
    private static final int MAX_NOTE = 1000;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditRepository audit;
    private final ProjectRepository projects;
    private final ProjectAccessService projectAccess;
    private final HubProperties props;
    private final MembershipService memberships;

    public SignupService(UserRepository users, PasswordEncoder encoder, PasswordPolicy passwordPolicy, AuditRepository audit,
                         ProjectRepository projects, ProjectAccessService projectAccess, HubProperties props, MembershipService memberships) {
        this.users = users;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.projects = projects;
        this.projectAccess = projectAccess;
        this.props = props;
        this.memberships = memberships;
    }

    /** Common identity fields. team/jobTitle/note are optional by signup type. */
    public record RegisterCommand(String loginId, String email, String password, String displayName,
                                  String companyName, String departmentName, String teamName,
                                  String jobTitle, String signupNote) {}
    public record RegisterResult(long id, String status, String requestedRole, boolean reopened, boolean firstAdminCreated) {}
    public record LoginIdAvailability(String loginId, boolean available, String message) {}

    public boolean firstAdminRequired() { return users.countApprovedAdmins() == 0; }

    /** Public signup helper. It only reports login-id availability; email existence is never exposed. */
    public LoginIdAvailability checkLoginId(String rawLoginId) {
        String loginId = normalizeLoginId(rawLoginId);
        validateLoginId(loginId);
        boolean available = users.findAuthByIdentifier(loginId).isEmpty();
        String message = available
                ? "사용 가능한 아이디입니다."
                : "이미 사용 중인 아이디입니다. 다른 아이디를 입력해 주세요.";
        return new LoginIdAvailability(loginId, available, message);
    }

    @Transactional
    public RegisterResult registerMember(RegisterCommand command) {
        return registerPending(command, "MEMBER", true);
    }

    @Transactional
    public RegisterResult registerAdmin(RegisterCommand command) {
        // Serialize the first-admin check so only that account receives demo bootstrap data.
        users.lockFirstAdminGuard();
        boolean firstAdmin = users.countApprovedAdmins() == 0;
        Validated v = validate(command, false);
        ensureIdentityUnusedForFirstAdmin(v.loginId(), v.email());
        try {
            long id = users.createAdmin(v.loginId(), v.email(), encoder.encode(command.password()), v.name(),
                    v.company(), v.department(), v.team());
            audit.add(id, null, firstAdmin ? "FIRST_ADMIN_SIGNUP" : "ADMIN_SIGNUP", "USER", id, "{\"role\":\"ADMIN\"}");
            if (firstAdmin && props.demoMode()) projects.create("Hub Demo Project", "Local validation project", id);
            return new RegisterResult(id, "APPROVED", "ADMIN", false, firstAdmin);
        } catch (DataIntegrityViolationException conflict) {
            throw new StateConflictException("아이디 또는 이메일이 방금 다른 계정에 사용되었습니다. 다른 값을 입력해 주세요.");
        }
    }

    private RegisterResult registerPending(RegisterCommand command, String requestedRole, boolean allowMemberExtras) {
        Validated v = validate(command, allowMemberExtras);
        var byLogin = users.findAuthByIdentifier(v.loginId());
        var byEmail = users.findAuthByEmail(v.email());
        if (byLogin.isPresent() || byEmail.isPresent()) {
            if (byLogin.isPresent() && byEmail.isPresent() && byLogin.get().id() == byEmail.get().id()
                    && "REJECTED".equals(byLogin.get().approvalStatus())
                    && requestedRole.equals(byLogin.get().requestedRole())) {
                if (!encoder.matches(command.password(), byLogin.get().passwordHash()))
                    throw new IllegalArgumentException("기존 가입 신청의 비밀번호를 확인해 주세요.");
                long id = byLogin.get().id();
                boolean reopened = users.reopenRejectedSignup(id, byLogin.get().passwordHash(), v.name(), v.company(),
                        v.department(), v.team(), v.jobTitle(), v.note(), requestedRole);
                if (!reopened) throw new StateConflictException("가입 신청 상태가 변경되었습니다. 화면을 새로고침해 주세요.");
                audit.add(null, null, "SIGNUP_REOPEN", "USER", id, "{\"role\":\"" + requestedRole + "\"}");
                return new RegisterResult(id, "PENDING", requestedRole, true, false);
            }
            if (byLogin.isPresent()) throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        try {
            long id = users.createSignup(v.loginId(), v.email(), encoder.encode(command.password()), v.name(), v.company(),
                    v.department(), v.team(), v.jobTitle(), v.note(), requestedRole);
            audit.add(null, null, "SIGNUP_REQUEST", "USER", id, "{\"role\":\"" + requestedRole + "\"}");
            return new RegisterResult(id, "PENDING", requestedRole, false, false);
        } catch (DataIntegrityViolationException conflict) {
            throw new StateConflictException("아이디 또는 이메일이 방금 다른 신청에 사용되었습니다. 다른 값을 입력해 주세요.");
        }
    }

    @Transactional
    public void approve(long userId, User admin, Long projectId) {
        UserRepository.AuthUser pending = users.findAuthById(userId)
                .filter(u -> "PENDING".equals(u.approvalStatus()))
                .orElseThrow(() -> new StateConflictException("이미 처리되었거나 대기 중인 가입 신청이 아닙니다."));
        if ("ADMIN".equals(pending.requestedRole()) && projectId != null)
            throw new IllegalArgumentException("ADMIN은 모든 프로젝트에 접근하므로 프로젝트 MEMBER로 중복 배정하지 않습니다.");
        if (projectId != null) projectAccess.requireAdmin(projectId, admin);
        if (!users.approveSignup(userId, admin.id()))
            throw new StateConflictException("가입 신청 상태가 변경되었습니다. 화면을 새로고침해 주세요.");
        audit.add(admin.id(), null, "SIGNUP_APPROVE", "USER", userId, "{\"role\":\"" + pending.requestedRole() + "\"}");
        if ("ADMIN".equals(pending.requestedRole())) {
            memberships.removeFromAllProjects(userId, admin, "SIGNUP_ADMIN_APPROVED");
        } else if (projectId != null) {
            memberships.add(projectId, userId, admin, "SIGNUP_APPROVED_WITH_PROJECT");
        }
    }

    @Transactional
    public void reject(long userId, long adminId, String reason) {
        String safeReason = optional(reason, MAX_NOTE);
        if (!users.rejectSignup(userId, safeReason)) throw new StateConflictException("이미 처리되었거나 대기 중인 가입 신청이 아닙니다.");
        audit.add(adminId, null, "SIGNUP_REJECT", "USER", userId, "{}");
    }

    private Validated validate(RegisterCommand command, boolean allowMemberExtras) {
        String loginId = normalizeLoginId(command.loginId());
        String email = normalizeEmail(command.email());
        String name = required(command.displayName(), "이름", MAX_NAME);
        String company = required(command.companyName(), "회사/조직명", MAX_COMPANY);
        String department = optional(command.departmentName(), MAX_PROFILE);
        String team = optional(command.teamName(), MAX_PROFILE);
        String jobTitle = allowMemberExtras ? optional(command.jobTitle(), MAX_PROFILE) : null;
        String note = allowMemberExtras ? optional(command.signupNote(), MAX_NOTE) : null;
        validateLoginId(loginId);
        validateEmail(email);
        passwordPolicy.validate(command.password());
        return new Validated(loginId, email, name, company, department, team, jobTitle, note);
    }

    private void ensureIdentityUnusedForFirstAdmin(String loginId, String email) {
        if (users.findAuthByIdentifier(loginId).isPresent()) throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        if (users.findAuthByEmail(email).isPresent()) throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
    }

    private record Validated(String loginId, String email, String name, String company, String department,
                             String team, String jobTitle, String note) {}

    private static void validateLoginId(String loginId) {
        if (!LOGIN_ID.matcher(loginId).matches()) throw new IllegalArgumentException("아이디는 영문/숫자/._- 조합 4~40자로 입력해 주세요.");
    }
    private static void validateEmail(String email) {
        if (email.length() > 255 || !email.contains("@") || email.startsWith("@") || email.endsWith("@"))
            throw new IllegalArgumentException("올바른 이메일을 입력해 주세요.");
    }
    private static String normalizeLoginId(String raw) { return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT); }
    private static String normalizeEmail(String raw) { return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT); }
    private static String required(String raw, String label, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        if (value.length() > max) throw new IllegalArgumentException(label + "이(가) 너무 깁니다.");
        return value;
    }
    private static String optional(String raw, int max) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isBlank()) return null;
        if (value.length() > max) throw new IllegalArgumentException("입력값이 너무 깁니다.");
        return value;
    }
}
