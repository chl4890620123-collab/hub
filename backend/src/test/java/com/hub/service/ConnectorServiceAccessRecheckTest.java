package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.connector.ReadOnlyConnector;
import com.hub.model.User;
import com.hub.repository.ConnectorPolicyRepository;
import com.hub.repository.ConnectorRepository;
import com.hub.repository.TimelineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorServiceAccessRecheckTest {
    @Test
    void importRechecksProjectAccessBeforeUsingProviderCredential() {
        ReadOnlyConnector adapter = mock(ReadOnlyConnector.class);
        when(adapter.type()).thenReturn("GITHUB");
        ConnectorRepository repository = mock(ConnectorRepository.class);
        DocumentService documents = mock(DocumentService.class);
        TimelineRepository timeline = mock(TimelineRepository.class);
        HubProperties props = mock(HubProperties.class);
        GoogleAccessTokenProvider googleTokens = mock(GoogleAccessTokenProvider.class);
        ExternalOAuthService externalOAuth = mock(ExternalOAuthService.class);
        ConnectorPolicyRepository policy = mock(ConnectorPolicyRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        SensitiveDataMaskingService piiMasking = mock(SensitiveDataMaskingService.class);

        User user = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
        org.mockito.Mockito.doThrow(new AccessDeniedException("removed")).when(access).requireAccess(10L, user);

        ConnectorService service = new ConnectorService(
                List.of(adapter), repository, documents, timeline, new ObjectMapper(), props,
                googleTokens, externalOAuth, policy, access, piiMasking
        );

        assertThrows(AccessDeniedException.class, () -> service.importItems(10L, "GITHUB", "repo", user));

        verify(adapter, never()).fetch(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(externalOAuth, never()).token(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }
}
