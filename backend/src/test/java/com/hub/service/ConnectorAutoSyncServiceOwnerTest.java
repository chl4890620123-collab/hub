package com.hub.service;

import com.hub.model.User;
import com.hub.repository.ConnectorRepository;
import com.hub.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class ConnectorAutoSyncServiceOwnerTest {
    @Test
    void autoSyncReplaysScopeAsRecordedOwnerOnly() {
        ConnectorRepository states = mock(ConnectorRepository.class);
        UserRepository users = mock(UserRepository.class);
        ConnectorService connectors = mock(ConnectorService.class);
        ConnectorRepository.SyncScope scope = new ConnectorRepository.SyncScope(10L, "GITHUB", "repo-a", 7L);
        User owner = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");

        when(states.allKnownScopes()).thenReturn(List.of(scope));
        when(users.findById(7L)).thenReturn(Optional.of(owner));

        ConnectorAutoSyncService service = new ConnectorAutoSyncService(states, users, connectors, true);
        service.syncAll();

        verify(connectors).importItems(10L, "GITHUB", "repo-a", owner);
    }

    @Test
    void inactiveOwnerDoesNotRunAutoSync() {
        ConnectorRepository states = mock(ConnectorRepository.class);
        UserRepository users = mock(UserRepository.class);
        ConnectorService connectors = mock(ConnectorService.class);
        ConnectorRepository.SyncScope scope = new ConnectorRepository.SyncScope(10L, "GITHUB", "repo-a", 7L);
        User suspended = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "SUSPENDED", false, "APPROVED");

        when(states.allKnownScopes()).thenReturn(List.of(scope));
        when(users.findById(7L)).thenReturn(Optional.of(suspended));

        ConnectorAutoSyncService service = new ConnectorAutoSyncService(states, users, connectors, true);
        service.syncAll();

        verify(connectors, never()).importItems(anyLong(), anyString(), anyString(), any(User.class));
    }
}
