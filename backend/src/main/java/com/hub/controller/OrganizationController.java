package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.OrganizationRepository;
import com.hub.service.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class OrganizationController {
    private final OrganizationRepository organizations;
    private final CurrentUserService current;

    public OrganizationController(OrganizationRepository organizations, CurrentUserService current) {
        this.organizations = organizations;
        this.current = current;
    }

    public record SignupOrganization(List<OrganizationRepository.Department> departments,
                                     List<OrganizationRepository.Team> teams) {}

    @GetMapping("/api/auth/signup/organization")
    public SignupOrganization signupOrganization() {
        return new SignupOrganization(organizations.departments(true), organizations.teams(true));
    }

    @GetMapping("/api/admin/organization")
    public SignupOrganization adminOrganization(Authentication authentication) {
        requireAdmin(authentication);
        return new SignupOrganization(organizations.departments(false), organizations.teams(false));
    }

    public record Named(String name) {}
    public record TeamInput(long departmentId, String name) {}
    public record ActiveInput(boolean active) {}

    @PostMapping("/api/admin/organization/departments")
    public Map<String,Object> createDepartment(@RequestBody Named request, Authentication authentication) {
        requireAdmin(authentication);
        try {
            return Map.of("id", organizations.createDepartment(request.name()), "status", "CREATED");
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("같은 이름의 부서가 이미 있습니다.");
        }
    }

    @PutMapping("/api/admin/organization/departments/{id}")
    public Map<String,Object> renameDepartment(@PathVariable long id, @RequestBody Named request, Authentication authentication) {
        requireAdmin(authentication);
        organizations.renameDepartment(id, request.name());
        return Map.of("status", "UPDATED");
    }

    @PatchMapping("/api/admin/organization/departments/{id}/active")
    public Map<String,Object> departmentActive(@PathVariable long id, @RequestBody ActiveInput request, Authentication authentication) {
        requireAdmin(authentication);
        organizations.setDepartmentActive(id, request.active());
        return Map.of("status", request.active() ? "ACTIVE" : "INACTIVE");
    }

    @PostMapping("/api/admin/organization/teams")
    public Map<String,Object> createTeam(@RequestBody TeamInput request, Authentication authentication) {
        requireAdmin(authentication);
        try {
            return Map.of("id", organizations.createTeam(request.departmentId(), request.name()), "status", "CREATED");
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이 부서에 같은 이름의 팀이 이미 있습니다.");
        }
    }

    @PutMapping("/api/admin/organization/teams/{id}")
    public Map<String,Object> renameTeam(@PathVariable long id, @RequestBody Named request, Authentication authentication) {
        requireAdmin(authentication);
        organizations.renameTeam(id, request.name());
        return Map.of("status", "UPDATED");
    }

    @PatchMapping("/api/admin/organization/teams/{id}/active")
    public Map<String,Object> teamActive(@PathVariable long id, @RequestBody ActiveInput request, Authentication authentication) {
        requireAdmin(authentication);
        organizations.setTeamActive(id, request.active());
        return Map.of("status", request.active() ? "ACTIVE" : "INACTIVE");
    }

    private User requireAdmin(Authentication authentication) {
        User user = current.requireOperational(authentication);
        if (!user.isAdmin()) throw new AccessDeniedException("관리자 권한이 필요합니다.");
        return user;
    }
}
