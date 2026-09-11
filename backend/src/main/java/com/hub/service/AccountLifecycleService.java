// account suspend/withdraw/reactivate rules are centralized so auth, membership, and TODO handoff cannot drift.
package com.hub.service;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.RefreshTokenRepository;
import com.hub.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AccountLifecycleService {
    private static final Set<String> STATUSES=Set.of("ACTIVE","SUSPENDED","WITHDRAWN");
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final MembershipService memberships;
    private final AuditRepository audit;

    public AccountLifecycleService(UserRepository users,RefreshTokenRepository refreshTokens,MembershipService memberships,AuditRepository audit){
        this.users=users;this.refreshTokens=refreshTokens;this.memberships=memberships;this.audit=audit;
    }

    @Transactional
    public int changeStatus(User actor,long userId,String requestedStatus,String reason){
        String status=normalize(requestedStatus);
        User target=users.findById(userId).orElseThrow(()->new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if(status.equals(target.accountStatus())) return 0; // Idempotent operational request: repeated clicks/retries must not duplicate handoff/audit work.
        if(target.withdrawn()&&!"WITHDRAWN".equals(status))throw new IllegalArgumentException("탈퇴 완료 계정은 다시 활성화하지 않습니다. 새 가입 절차를 사용해 주세요.");
        if(target.isAdmin()&&target.active()&&!"ACTIVE".equals(status)&&users.countActiveAdmins()<=1)
            throw new IllegalArgumentException("마지막 활성 관리자 계정은 정지/탈퇴할 수 없습니다.");
        if(actor!=null&&actor.id()==target.id()&&"SUSPENDED".equals(status))
            throw new IllegalArgumentException("현재 로그인한 본인 계정을 직접 정지할 수 없습니다. 탈퇴 또는 다른 관리자 처리를 사용해 주세요.");

        int queued=0;
        if(!"ACTIVE".equals(status)) queued=memberships.removeFromAllProjects(userId,actor,"WITHDRAWN".equals(status)?"ACCOUNT_WITHDRAWAL":"ACCOUNT_SUSPEND");
        if(!users.setAccountStatus(userId,status,reason))throw new StateConflictException("계정 상태를 변경할 수 없습니다.");
        refreshTokens.revokeAllForUser(userId,"ACCOUNT_"+status);
        audit.add(actor==null?userId:actor.id(),null,"USER_ACCOUNT_STATUS","USER",userId,
                "{\"status\":\""+status+"\",\"reassignmentCount\":"+queued+"}");
        return queued;
    }

    private static String normalize(String raw){
        String status=raw==null?"":raw.trim().toUpperCase();
        if(!STATUSES.contains(status))throw new IllegalArgumentException("지원하지 않는 계정 상태입니다.");
        return status;
    }
}
