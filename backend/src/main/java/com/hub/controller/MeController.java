// self-service profile updates are audited; withdrawal is irreversible and requires password confirmation.
package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.UserRepository;
import com.hub.service.AccountLifecycleService;
import com.hub.service.AuthException;
import com.hub.service.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final CurrentUserService current;private final UserRepository users;private final AuditRepository audit;
    private final PasswordEncoder encoder;private final AccountLifecycleService accounts;
    public MeController(CurrentUserService current,UserRepository users,AuditRepository audit,PasswordEncoder encoder,AccountLifecycleService accounts){
        this.current=current;this.users=users;this.audit=audit;this.encoder=encoder;this.accounts=accounts;}
    @GetMapping public User me(Authentication auth){return current.require(auth);}

    public record ProfileUpdate(String departmentName,String teamName,String jobTitle){}
    @PatchMapping("/profile")
    public User profile(@RequestBody ProfileUpdate request,Authentication auth){
        User user=current.requireOperational(auth);String department=trim(request.departmentName());String team=trim(request.teamName());String jobTitle=trim(request.jobTitle());
        if(department!=null&&department.length()>200)throw new IllegalArgumentException("부서는 200자 이하여야 합니다.");
        if(team!=null&&team.length()>200)throw new IllegalArgumentException("팀은 200자 이하여야 합니다.");
        if(jobTitle!=null&&jobTitle.length()>200)throw new IllegalArgumentException("직급/직책은 200자 이하여야 합니다.");
        users.updateProfile(user.id(),department,team,jobTitle);
        audit.add(user.id(),null,"USER_PROFILE_UPDATE","USER",user.id(),"{\"departmentChanged\":"+!same(user.departmentName(),department)+",\"teamChanged\":"+!same(user.teamName(),team)+"}");
        return users.findById(user.id()).orElseThrow();
    }

    public record WithdrawRequest(String currentPassword,String reason){}
    @PostMapping("/withdraw")
    public java.util.Map<String,Object> withdraw(@RequestBody WithdrawRequest request,Authentication auth){
        User user=current.requireOperational(auth);
        var account=users.findAuthById(user.id()).orElseThrow(()->new AuthException("INVALID_CREDENTIALS","계정을 찾을 수 없습니다."));
        if(!encoder.matches(request.currentPassword()==null?"":request.currentPassword(),account.passwordHash()))throw new AuthException("INVALID_CREDENTIALS","현재 비밀번호가 올바르지 않습니다.");
        int queued=accounts.changeStatus(user,user.id(),"WITHDRAWN",request.reason());
        return java.util.Map.of("status","WITHDRAWN","reassignmentCount",queued);
    }

    private static boolean same(String a,String b){return java.util.Objects.equals(trim(a),trim(b));}
    private static String trim(String value){if(value==null)return null;String v=value.trim();return v.isBlank()?null:v;}
}
