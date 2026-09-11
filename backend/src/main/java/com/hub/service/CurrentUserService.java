package com.hub.service;

import com.hub.model.User;
import com.hub.repository.RefreshTokenRepository;
import com.hub.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    public CurrentUserService(UserRepository users, RefreshTokenRepository refreshTokens) {
        this.users=users; this.refreshTokens=refreshTokens;
    }

    public User require(Authentication authentication) {
        if(authentication==null||!authentication.isAuthenticated()) throw new AuthException("AUTH_REQUIRED","로그인이 필요합니다.");
        UserRepository.AuthUser account=users.findAuthByEmail(authentication.getName())
                .filter(UserRepository.AuthUser::active)
                .filter(user -> "APPROVED".equals(user.approvalStatus()))
                .orElseThrow(()->new AuthException("SESSION_INVALID","세션이 유효하지 않습니다. 다시 로그인해 주세요."));
        if(authentication instanceof JwtAuthenticationToken jwtAuth) {
            Number ver=jwtAuth.getToken().getClaim("ver");
            String sid=jwtAuth.getToken().getClaimAsString("sid");
            if(ver==null||ver.longValue()!=account.authVersion()||sid==null||!refreshTokens.isFamilyActive(sid))
                throw new AuthException("SESSION_INVALID","로그인 세션이 종료되었거나 계정 정보가 변경되었습니다. 다시 로그인해 주세요.");
        }
        return account.asUser();
    }

    public User requireOperational(Authentication authentication) {
        User user=require(authentication);
        if(user.mustChangePassword()) throw new AuthException("PASSWORD_CHANGE_REQUIRED","초기 비밀번호를 먼저 변경해 주세요.");
        return user;
    }
}
