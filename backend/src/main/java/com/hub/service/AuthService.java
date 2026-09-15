package com.hub.service;

import com.hub.config.JwtProperties;
import com.hub.model.User;
import com.hub.repository.RefreshTokenRepository;
import com.hub.repository.UserRepository;
import com.hub.util.Hashing;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties props;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, JwtProperties props) {
        this.users=users; this.refreshTokens=refreshTokens; this.encoder=encoder; this.jwt=jwt; this.props=props;
        this.dummyHash=encoder.encode("hub-dummy-password-never-used-1");
    }

    @Transactional(noRollbackFor = AuthException.class)
    public SessionTokens login(String identifier, String password, String requestedRole, String userAgent, String ipAddress) {
        String normalized=identifier==null?"":identifier.trim().toLowerCase();
        users.clearExpiredLockByIdentifier(normalized);
        var optional=users.findAuthByIdentifier(normalized);
        if(optional.isEmpty()) { encoder.matches(password==null?"":password,dummyHash); throw invalidCredentials(); }
        UserRepository.AuthUser account=optional.get();
        if(account.isLocked(Instant.now())) throw new AuthException("ACCOUNT_LOCKED","로그인 시도가 많아 잠시 잠겼습니다.");
        if(!encoder.matches(password==null?"":password,account.passwordHash())) {
            users.recordFailedLogin(account.id(),props.maxFailedAttempts(),props.lockDuration());
            throw invalidCredentials();
        }
        // Only reveal approval state after the applicant proved possession of the password.
        if("PENDING".equals(account.approvalStatus()))
            throw new AuthException("ACCOUNT_PENDING","가입 신청이 관리자 승인 대기 중입니다.");
        if("REJECTED".equals(account.approvalStatus())) {
            String suffix=account.rejectionReason()==null||account.rejectionReason().isBlank()?"":" 사유: "+account.rejectionReason();
            throw new AuthException("ACCOUNT_REJECTED","가입 신청이 승인되지 않았습니다."+suffix+" 회원가입 신청에서 내용을 수정해 다시 신청할 수 있습니다.");
        }
        if(!account.active()) {
            if("WITHDRAWN".equals(account.accountStatus())) throw new AuthException("ACCOUNT_WITHDRAWN","탈퇴 처리된 계정입니다.");
            throw new AuthException("ACCOUNT_SUSPENDED","정지된 계정입니다. 관리자에게 문의해 주세요.");
        }
        if (requestedRole != null && !requestedRole.isBlank()
                && !requestedRole.trim().toUpperCase().equals(account.globalRole())) {
            String expected = "ADMIN".equals(account.globalRole()) ? "관리자" : "일반회원";
            throw new AuthException("ROLE_MISMATCH", expected + " 로그인 화면에서 로그인해 주세요.");
        }
        users.recordSuccessfulLogin(account.id());
        return issueSession(account.asUser(),account.authVersion(),newFamilyId(),userAgent,ipAddress);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public SessionTokens refresh(String rawRefreshToken,String userAgent,String ipAddress) {
        if(rawRefreshToken==null||rawRefreshToken.isBlank()) throw new AuthException("REFRESH_REQUIRED","다시 로그인해 주세요.");
        String oldHash=Hashing.sha256(rawRefreshToken);
        RefreshTokenRepository.TokenState state=refreshTokens.find(oldHash)
                .orElseThrow(()->new AuthException("INVALID_REFRESH","세션이 만료되었습니다. 다시 로그인해 주세요."));
        Instant now=Instant.now();
        if(state.expired(now)) throw new AuthException("INVALID_REFRESH","세션이 만료되었습니다. 다시 로그인해 주세요.");
        if(state.revoked()) {
            if("ROTATED".equals(state.revokedReason())) refreshTokens.revokeFamily(state.familyId(),"REPLAY_DETECTED");
            throw new AuthException("INVALID_REFRESH","세션이 만료되었습니다. 다시 로그인해 주세요.");
        }
        UserRepository.AuthUser account=users.findAuthById(state.userId())
                .filter(UserRepository.AuthUser::active)
                .filter(user -> "APPROVED".equals(user.approvalStatus()))
                .orElseThrow(()->new AuthException("INVALID_REFRESH","세션을 갱신할 수 없습니다."));
        if(!refreshTokens.consumeForRotation(oldHash)) {
            RefreshTokenRepository.TokenState latest=refreshTokens.find(oldHash).orElse(state);
            if("ROTATED".equals(latest.revokedReason())) refreshTokens.revokeFamily(latest.familyId(),"REPLAY_DETECTED");
            throw new AuthException("INVALID_REFRESH","세션이 만료되었습니다. 다시 로그인해 주세요.");
        }
        String familyId=state.familyId()==null||state.familyId().isBlank()?newFamilyId():state.familyId();
        return issueSession(account.asUser(),account.authVersion(),familyId,userAgent,ipAddress);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if(rawRefreshToken==null||rawRefreshToken.isBlank()) return;
        refreshTokens.find(Hashing.sha256(rawRefreshToken)).ifPresent(state -> refreshTokens.revokeFamily(state.familyId(),"LOGOUT"));
    }

    @Transactional
    public void changePassword(User user,String currentPassword,String newPassword,PasswordPolicy policy) {
        var account=users.findAuthById(user.id()).orElseThrow(()->new AuthException("INVALID_CREDENTIALS","계정을 찾을 수 없습니다."));
        if(!encoder.matches(currentPassword==null?"":currentPassword,account.passwordHash()))
            throw new AuthException("INVALID_CREDENTIALS","현재 비밀번호가 올바르지 않습니다.");
        policy.validate(newPassword);
        if(encoder.matches(newPassword,account.passwordHash())) throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        users.updatePassword(user.id(),encoder.encode(newPassword));
        refreshTokens.revokeAllForUser(user.id(),"PASSWORD_CHANGED");
    }

    private SessionTokens issueSession(User user,long authVersion,String familyId,String userAgent,String ipAddress) {
        refreshTokens.deleteExpired();
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String rawRefresh=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant refreshExpiresAt=Instant.now().plus(props.refreshTtl());
        refreshTokens.create(user.id(),familyId,Hashing.sha256(rawRefresh),refreshExpiresAt,userAgent,ipAddress);
        JwtService.AccessToken access=jwt.issue(user,authVersion,familyId);
        return new SessionTokens(user,access.value(),access.expiresAt(),rawRefresh,refreshExpiresAt);
    }

    private static String newFamilyId(){return UUID.randomUUID().toString();}
    private static AuthException invalidCredentials(){return new AuthException("INVALID_CREDENTIALS","아이디/이메일 또는 비밀번호를 확인해 주세요.");}
    public record SessionTokens(User user,String accessToken,Instant accessExpiresAt,String refreshToken,Instant refreshExpiresAt){}
}
