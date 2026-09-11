// ADMIN operations use lifecycle/membership services so account, access, history, and TODO handoff change together.
package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.RefreshTokenRepository;
import com.hub.repository.UserRepository;
import com.hub.service.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final List<String> GLOBAL_ROLES = List.of("ADMIN", "MEMBER");

    private final CurrentUserService current;private final UserRepository users;private final ProjectRepository projects;
    private final PasswordEncoder encoder;private final PasswordPolicy passwordPolicy;private final ProjectAccessService access;
    private final AuditRepository audit;private final RefreshTokenRepository refreshTokens;private final SignupService signup;
    private final MembershipService memberships;private final AccountLifecycleService accounts;private final TodoReassignmentService reassignments;

    public AdminController(CurrentUserService current,UserRepository users,ProjectRepository projects,PasswordEncoder encoder,
                           PasswordPolicy passwordPolicy,ProjectAccessService access,AuditRepository audit,
                           RefreshTokenRepository refreshTokens,SignupService signup,MembershipService memberships,
                           AccountLifecycleService accounts,TodoReassignmentService reassignments){
        this.current=current;this.users=users;this.projects=projects;this.encoder=encoder;this.passwordPolicy=passwordPolicy;
        this.access=access;this.audit=audit;this.refreshTokens=refreshTokens;this.signup=signup;this.memberships=memberships;
        this.accounts=accounts;this.reassignments=reassignments;
    }

    private User requireAdmin(Authentication authentication){User user=current.requireOperational(authentication);if(!user.isAdmin())throw new AccessDeniedException("Admin only");return user;}

    @GetMapping("/users") public List<User> listUsers(Authentication authentication){requireAdmin(authentication);return users.list();}
    @GetMapping("/signup-applications") public List<UserRepository.SignupApplication> signupApplications(Authentication authentication){requireAdmin(authentication);return users.listPendingApplications();}

    public record ApproveSignup(Long projectId){}
    @PostMapping("/signup-applications/{userId}/approve")
    public Map<String,Object> approveSignup(@PathVariable long userId,@RequestBody(required=false) ApproveSignup request,Authentication authentication){
        User actor=requireAdmin(authentication);Long projectId=request==null?null:request.projectId();signup.approve(userId,actor,projectId);
        return Map.of("status","APPROVED","projectAssigned",projectId!=null);
    }

    public record RejectSignup(String reason){}
    @PostMapping("/signup-applications/{userId}/reject")
    public Map<String,Object> rejectSignup(@PathVariable long userId,@RequestBody(required=false) RejectSignup request,Authentication authentication){
        User actor=requireAdmin(authentication);signup.reject(userId,actor.id(),request==null?null:request.reason());refreshTokens.revokeAllForUser(userId,"SIGNUP_REJECTED");
        return Map.of("status","REJECTED");
    }

    public record MemberRole(long userId){}
    @PutMapping("/projects/{projectId}/members")
    public Map<String,Object> saveProjectMember(@PathVariable long projectId,@RequestBody MemberRole request,Authentication authentication){
        User admin=requireAdmin(authentication);access.requireAdmin(projectId,admin);memberships.add(projectId,request.userId(),admin,"ADMIN_ASSIGN");return Map.of("status","SAVED");
    }

    @DeleteMapping("/projects/{projectId}/members/{userId}")
    public Map<String,Object> removeProjectMember(@PathVariable long projectId,@PathVariable long userId,Authentication authentication){
        User admin=requireAdmin(authentication);access.requireAdmin(projectId,admin);int queued=memberships.remove(projectId,userId,admin,"ADMIN_REMOVE");
        return Map.of("status","REMOVED","reassignmentCount",queued);
    }

    public record MoveMember(long userId,long toProjectId){}
    @PostMapping("/projects/{fromProjectId}/members/move")
    public Map<String,Object> moveProjectMember(@PathVariable long fromProjectId,@RequestBody MoveMember request,Authentication authentication){
        User admin=requireAdmin(authentication);access.requireAdmin(fromProjectId,admin);access.requireAdmin(request.toProjectId(),admin);
        int queued=memberships.move(fromProjectId,request.toProjectId(),request.userId(),admin);
        return Map.of("status","MOVED","reassignmentCount",queued);
    }

    public record AccountStatusChange(String status,String reason){}
    @PatchMapping("/users/{userId}/status")
    public Map<String,Object> accountStatus(@PathVariable long userId,@RequestBody AccountStatusChange request,Authentication authentication){
        User actor=requireAdmin(authentication);int queued=accounts.changeStatus(actor,userId,request.status(),request.reason());
        return Map.of("status","UPDATED","reassignmentCount",queued);
    }

    public record RoleChange(String role){}
    @PatchMapping("/users/{userId}/role")
    public Map<String,Object> role(@PathVariable long userId,@RequestBody RoleChange request,Authentication authentication){
        User actor=requireAdmin(authentication);User target=requireTarget(userId);String role=normalizeRole(request.role());
        if(target.withdrawn())throw new IllegalArgumentException("탈퇴 계정의 권한은 변경할 수 없습니다.");
        if(!target.active())throw new IllegalArgumentException("정지된 계정의 권한은 변경하지 않습니다. 먼저 계정을 활성화해 주세요.");
        if(role.equals(target.globalRole())) return Map.of("status","UNCHANGED");
        protectLastAdmin(target,target.active(),role);
        if(actor.id()==target.id()&&!"ADMIN".equals(role))throw new IllegalArgumentException("현재 로그인한 관리자 본인의 권한은 내릴 수 없습니다.");
        if("ADMIN".equals(role)&&!target.isAdmin())memberships.removeFromAllProjects(userId,actor,"ROLE_PROMOTED_ADMIN");
        users.setRole(userId,role);refreshTokens.revokeAllForUser(userId,"ROLE_CHANGED");
        audit.add(actor.id(),null,"USER_ROLE_CHANGE","USER",userId,"{\"role\":\""+role+"\"}");return Map.of("status","UPDATED");
    }

    public record ResetPassword(String temporaryPassword){}
    @PostMapping("/users/{userId}/reset-password")
    public Map<String,Object> resetPassword(@PathVariable long userId,@RequestBody ResetPassword request,Authentication authentication){
        User actor=requireAdmin(authentication);User target=requireTarget(userId);if(target.withdrawn())throw new IllegalArgumentException("탈퇴 계정의 비밀번호는 재설정할 수 없습니다.");
        passwordPolicy.validate(request.temporaryPassword());users.resetPassword(userId,encoder.encode(request.temporaryPassword()));refreshTokens.revokeAllForUser(userId,"ADMIN_PASSWORD_RESET");
        audit.add(actor.id(),null,"USER_PASSWORD_RESET","USER",userId,"{}");return Map.of("status","RESET","mustChangePassword",true);
    }

    @GetMapping("/projects/{projectId}/reassignments")
    public List<Map<String,Object>> reassignments(@PathVariable long projectId,Authentication authentication){User admin=requireAdmin(authentication);access.requireAdmin(projectId,admin);return reassignments.pending(projectId);}

    public record ResolveReassignment(long newAssigneeId){}
    @PostMapping("/reassignments/{requestId}/resolve")
    public Map<String,Object> resolveReassignment(@PathVariable long requestId,@RequestBody ResolveReassignment request,Authentication authentication){
        User admin=requireAdmin(authentication);reassignments.resolve(requestId,request.newAssigneeId(),admin);return Map.of("status","RESOLVED");
    }

    private User requireTarget(long userId){return users.findById(userId).orElseThrow(()->new IllegalArgumentException("사용자를 찾을 수 없습니다."));}
    private void protectLastAdmin(User target,boolean nextActive,String nextRole){if(target.active()&&target.isAdmin()&&(!nextActive||!"ADMIN".equals(nextRole))&&users.countActiveAdmins()<=1)throw new IllegalArgumentException("마지막 활성 관리자 계정은 정지/탈퇴하거나 MEMBER로 변경할 수 없습니다.");}
    private static String normalizeRole(String raw){String r=raw==null?"MEMBER":raw.trim().toUpperCase(Locale.ROOT);if(!GLOBAL_ROLES.contains(r))throw new IllegalArgumentException("Unsupported role: "+r);return r;}
}
