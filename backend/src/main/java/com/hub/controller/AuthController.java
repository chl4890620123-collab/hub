package com.hub.controller;

import com.hub.config.JwtProperties;
import com.hub.model.User;
import com.hub.service.AuthService;
import com.hub.service.CurrentUserService;
import com.hub.service.PasswordPolicy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUser;
    private final PasswordPolicy passwordPolicy;
    private final JwtProperties props;

    public AuthController(AuthService authService, CurrentUserService currentUser, PasswordPolicy passwordPolicy, JwtProperties props) {
        this.authService = authService;
        this.currentUser = currentUser;
        this.passwordPolicy = passwordPolicy;
        this.props = props;
    }

    public record LoginRequest(@NotBlank String identifier, @NotBlank String password, String role) {}
    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                                     HttpServletResponse response) {
        var tokens = authService.login(request.identifier(), request.password(), request.role(), servletRequest.getHeader("User-Agent"), servletRequest.getRemoteAddr());
        writeSessionCookies(response, tokens);
        return Map.of("user", tokens.user(), "accessExpiresAt", tokens.accessExpiresAt().toString());
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refresh = cookie(request, JwtProperties.REFRESH_COOKIE);
        var tokens = authService.refresh(refresh, request.getHeader("User-Agent"), request.getRemoteAddr());
        writeSessionCookies(response, tokens);
        return Map.of("user", tokens.user(), "accessExpiresAt", tokens.accessExpiresAt().toString());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookie(request, JwtProperties.REFRESH_COOKIE));
        clearCookies(response);
        return Map.of("status", "LOGGED_OUT");
    }

    @PostMapping("/password")
    public Map<String, String> changePassword(@Valid @RequestBody PasswordChangeRequest request, Authentication authentication,
                                               HttpServletResponse response) {
        User user = currentUser.require(authentication);
        authService.changePassword(user, request.currentPassword(), request.newPassword(), passwordPolicy);
        clearCookies(response);
        return Map.of("status", "PASSWORD_CHANGED", "message", "비밀번호가 변경되었습니다. 다시 로그인해 주세요.");
    }

    private void writeSessionCookies(HttpServletResponse response, AuthService.SessionTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHeader(JwtProperties.ACCESS_COOKIE, tokens.accessToken(), props.accessTtl(), "/"));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHeader(JwtProperties.REFRESH_COOKIE, tokens.refreshToken(), props.refreshTtl(), "/api/auth"));
    }

    private String cookieHeader(String name, String value, Duration maxAge, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.secureCookies())
                .sameSite(props.sameSite())
                .path(path)
                .maxAge(maxAge)
                .build().toString();
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHeader(JwtProperties.ACCESS_COOKIE, "", Duration.ZERO, "/"));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHeader(JwtProperties.REFRESH_COOKIE, "", Duration.ZERO, "/api/auth"));
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
