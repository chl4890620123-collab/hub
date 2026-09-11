// separate MEMBER and ADMIN signup endpoints; neither duplicates login/JWT logic.
package com.hub.controller;

import com.hub.service.SignupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    private final SignupService signup;
    public RegistrationController(SignupService signup) { this.signup = signup; }

    public record MemberSignupRequest(
            @NotBlank String loginId, @NotBlank String email, @NotBlank String password,
            @NotBlank String displayName, @NotBlank String companyName,
            String departmentName, String teamName, String jobTitle, String signupNote
    ) {}

    public record AdminSignupRequest(
            @NotBlank String loginId, @NotBlank String email, @NotBlank String password,
            @NotBlank String displayName, @NotBlank String companyName,
            String departmentName, String teamName
    ) {}

    @GetMapping("/setup-status")
    public Map<String,Object> setupStatus() {
        return Map.of("firstAdminRequired", signup.firstAdminRequired());
    }

    @GetMapping("/check-login-id")
    public Map<String,Object> checkLoginId(@RequestParam String loginId) {
        var result = signup.checkLoginId(loginId);
        return Map.of(
                "loginId", result.loginId(),
                "available", result.available(),
                "message", result.message()
        );
    }

    @PostMapping("/signup/member")
    public Map<String,Object> memberSignup(@Valid @RequestBody MemberSignupRequest request) {
        var result = signup.registerMember(new SignupService.RegisterCommand(
                request.loginId(), request.email(), request.password(), request.displayName(), request.companyName(),
                request.departmentName(), request.teamName(), request.jobTitle(), request.signupNote()));
        return Map.of("status", result.status(), "requestedRole", "MEMBER", "reopened", result.reopened(),
                "message", "회원가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.");
    }

    @PostMapping("/signup/admin")
    public Map<String,Object> adminSignup(@Valid @RequestBody AdminSignupRequest request) {
        var result = signup.registerAdmin(new SignupService.RegisterCommand(
                request.loginId(), request.email(), request.password(), request.displayName(), request.companyName(),
                request.departmentName(), request.teamName(), null, null));
        String message = result.firstAdminCreated()
                ? "최초 관리자 계정이 생성되었습니다. 지금 로그인할 수 있습니다."
                : "관리자 계정이 생성되었습니다. 지금 로그인할 수 있습니다.";
        return Map.of("status", result.status(), "requestedRole", "ADMIN", "reopened", result.reopened(),
                "firstAdminCreated", result.firstAdminCreated(), "message", message);
    }
}
