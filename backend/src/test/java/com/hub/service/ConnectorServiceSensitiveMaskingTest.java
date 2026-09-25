package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.connector.ExternalContent;
import com.hub.connector.ReadOnlyConnector;
import com.hub.model.User;
import com.hub.repository.ConnectorPolicyRepository;
import com.hub.repository.ConnectorRepository;
import com.hub.repository.TimelineRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConnectorServiceSensitiveMaskingTest {
    @Test
    void connectorSnapshotStoresMaskedSearchableFields() {
        ReadOnlyConnector adapter = mock(ReadOnlyConnector.class);
        when(adapter.type()).thenReturn("GITHUB");
        when(adapter.fetch("repo", "token")).thenReturn(List.of(new ExternalContent(
                "repo:item-1", "GIT_ISSUE", "secret-title", "secret-body",
                null, null, "secret-author", "https://github.com/example/repo/issues/1",
                OffsetDateTime.parse("2026-09-25T00:00:00Z"), Map.of("location", "secret-location")
        )));

        ConnectorRepository repository = mock(ConnectorRepository.class);
        DocumentService documents = mock(DocumentService.class);
        TimelineRepository timeline = mock(TimelineRepository.class);
        HubProperties props = mock(HubProperties.class);
        GoogleAccessTokenProvider googleTokens = mock(GoogleAccessTokenProvider.class);
        ExternalOAuthService externalOAuth = mock(ExternalOAuthService.class);
        ConnectorPolicyRepository policy = mock(ConnectorPolicyRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        SensitiveDataMaskingService masking = mock(SensitiveDataMaskingService.class);

        when(policy.isEnabled("GITHUB")).thenReturn(true);
        when(externalOAuth.token(7L, "GITHUB")).thenReturn("token");
        when(masking.mask(any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return value == null ? null : "MASK[" + value + "]";
        });

        ConnectorService service = new ConnectorService(
                List.of(adapter), repository, documents, timeline, new ObjectMapper(), props,
                googleTokens, externalOAuth, policy, access, masking
        );
        User user = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");

        service.importItems(10L, "GITHUB", "repo", user);

        verify(repository).saveItem(
                eq(10L), isNull(), eq("GITHUB"), eq("repo:item-1"), eq("GIT_ISSUE"),
                eq("MASK[secret-title]"), eq("MASK[secret-body]"), eq("MASK[secret-author]"),
                eq("https://github.com/example/repo/issues/1"), any(), anyString()
        );
        verify(repository).saveSyncState(10L, "GITHUB", "repo", 7L, "SUCCESS", null, 1);
    }

    @Test
    void failedNormalizedImportDoesNotLeaveSearchableSnapshot() {
        ReadOnlyConnector adapter = mock(ReadOnlyConnector.class);
        when(adapter.type()).thenReturn("GITHUB");
        when(adapter.fetch("repo", "token")).thenReturn(List.of(new ExternalContent(
                "repo:item-2", "GIT_ISSUE", "title", "body",
                null, null, "author", "https://github.com/example/repo/issues/2",
                OffsetDateTime.parse("2026-09-25T00:00:00Z"), Map.of()
        )));
        ConnectorRepository repository = mock(ConnectorRepository.class);
        DocumentService documents = mock(DocumentService.class);
        TimelineRepository timeline = mock(TimelineRepository.class);
        HubProperties props = mock(HubProperties.class);
        GoogleAccessTokenProvider googleTokens = mock(GoogleAccessTokenProvider.class);
        ExternalOAuthService externalOAuth = mock(ExternalOAuthService.class);
        ConnectorPolicyRepository policy = mock(ConnectorPolicyRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        SensitiveDataMaskingService masking = mock(SensitiveDataMaskingService.class);
        when(policy.isEnabled("GITHUB")).thenReturn(true);
        when(externalOAuth.token(7L, "GITHUB")).thenReturn("token");
        when(masking.mask(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalArgumentException("parse failed")).when(documents)
                .importExternalText(eq(10L), eq("GITHUB"), eq("GITHUB:repo:item-2"), eq("title"), eq("body"), any(User.class));

        ConnectorService service = new ConnectorService(
                List.of(adapter), repository, documents, timeline, new ObjectMapper(), props,
                googleTokens, externalOAuth, policy, access, masking
        );
        User user = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");

        service.importItems(10L, "GITHUB", "repo", user);

        verify(repository, never()).saveItem(
                anyLong(), any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any()
        );
        verify(repository).saveSyncState(
                eq(10L), eq("GITHUB"), eq("repo"), eq(7L), eq("SUCCESS"),
                contains("읽을 수 없는 파일 1건"), eq(0)
        );
    }
}
