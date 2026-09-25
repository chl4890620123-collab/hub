package com.hub.service;

import com.hub.model.User;
import com.hub.repository.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AccountLifecycleConnectorCleanupTest {
    @Test
    void withdrawalClearsConnectorScopeOwnershipAndCredentials() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        MembershipService memberships = mock(MembershipService.class);
        AuditRepository audit = mock(AuditRepository.class);
        ConnectorAccountRepository connectorAccounts = mock(ConnectorAccountRepository.class);
        ConnectorRepository connectorSync = mock(ConnectorRepository.class);

        AccountLifecycleService service = new AccountLifecycleService(
                users, refreshTokens, memberships, audit, connectorAccounts, connectorSync
        );
        User target = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");

        when(users.findById(7L)).thenReturn(Optional.of(target));
        when(users.setAccountStatus(7L, "WITHDRAWN", "bye")).thenReturn(true);
        when(memberships.removeFromAllProjects(7L, target, "ACCOUNT_WITHDRAWAL")).thenReturn(2);

        int queued = service.changeStatus(target, 7L, "WITHDRAWN", "bye");

        assertEquals(2, queued);
        verify(connectorSync).clearSyncOwnersForUser(7L);
        verify(connectorAccounts).disconnectAllForUser(7L);
        verify(refreshTokens).revokeAllForUser(7L, "ACCOUNT_WITHDRAWN");
    }

    @Test
    void suspensionKeepsConnectorCredentialsForPossibleReactivation() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        MembershipService memberships = mock(MembershipService.class);
        AuditRepository audit = mock(AuditRepository.class);
        ConnectorAccountRepository connectorAccounts = mock(ConnectorAccountRepository.class);
        ConnectorRepository connectorSync = mock(ConnectorRepository.class);

        AccountLifecycleService service = new AccountLifecycleService(
                users, refreshTokens, memberships, audit, connectorAccounts, connectorSync
        );
        User target = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");

        when(users.findById(7L)).thenReturn(Optional.of(target));
        when(users.setAccountStatus(7L, "SUSPENDED", "leave")).thenReturn(true);

        service.changeStatus(new User(99L, "admin", "admin@example.test", "Admin",
                "Hub", null, null, null, "ADMIN", "ACTIVE", false, "APPROVED"),
                7L, "SUSPENDED", "leave");

        verify(connectorSync, never()).clearSyncOwnersForUser(anyLong());
        verify(connectorAccounts, never()).disconnectAllForUser(anyLong());
    }
}
