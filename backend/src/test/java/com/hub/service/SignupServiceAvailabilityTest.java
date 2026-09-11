package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.repository.AuditRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceAvailabilityTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock AuditRepository audit;
    @Mock ProjectRepository projects;
    @Mock ProjectAccessService projectAccess;
    @Mock MembershipService memberships;

    private SignupService signup;

    @BeforeEach
    void setUp() {
        HubProperties props = new HubProperties(null, null, false, null, null,
                0, 0, 0, 0, 0, null, null, null, null, null, null, null);
        signup = new SignupService(users, encoder, new PasswordPolicy(), audit, projects, projectAccess, props, memberships);
    }

    @Test
    void availableLoginIdIsNormalized() {
        when(users.findAuthByIdentifier("new.user")).thenReturn(Optional.empty());
        SignupService.LoginIdAvailability result = signup.checkLoginId("  New.User  ");
        assertEquals("new.user", result.loginId());
        assertTrue(result.available());
        assertTrue(result.message().contains("사용 가능"));
    }

    @Test
    void existingLoginIdIsUnavailable() {
        UserRepository.AuthUser existing = new UserRepository.AuthUser(
                7L, "taken", "taken@example.com", "hash", "사용자", "회사", null, null, null,
                "MEMBER", "MEMBER", "ACTIVE", false, "APPROVED", null, 0, null, 1L);
        when(users.findAuthByIdentifier("taken")).thenReturn(Optional.of(existing));
        SignupService.LoginIdAvailability result = signup.checkLoginId("taken");
        assertFalse(result.available());
        assertTrue(result.message().contains("이미 사용"));
    }

    @Test
    void invalidLoginIdIsRejectedBeforeDatabaseLookup() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> signup.checkLoginId("ab"));
        assertTrue(error.getMessage().contains("4~40자"));
    }
}
