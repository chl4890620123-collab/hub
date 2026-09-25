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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorServiceDeletedSourceTest {
    @Test
    void archivedExternalItemIsNotAutomaticallyRestoredDuringResync() {
        ReadOnlyConnector adapter = mock(ReadOnlyConnector.class);
        ConnectorRepository repository = mock(ConnectorRepository.class);
        DocumentService documents = mock(DocumentService.class);
        TimelineRepository timeline = mock(TimelineRepository.class);
        HubProperties props = mock(HubProperties.class);
        GoogleAccessTokenProvider googleTokens = mock(GoogleAccessTokenProvider.class);
        ExternalOAuthService externalOAuth = mock(ExternalOAuthService.class);
        ConnectorPolicyRepository policy = mock(ConnectorPolicyRepository.class);

        when(adapter.type()).thenReturn("GITHUB");
        when(adapter.fetch("repo", "token")).thenReturn(List.of(new ExternalContent(
                "repo:item-2", "GIT_ISSUE", "보관한 이슈", "복원 전에는 다시 들어오면 안 되는 내용",
                null, null, "Alice", "https://github.com/example/repo/issues/2",
                OffsetDateTime.parse("2026-09-25T00:00:00Z"), Map.of()
        )));
        when(policy.isEnabled("GITHUB")).thenReturn(true);
        when(externalOAuth.token(7L, "GITHUB")).thenReturn("token");
        when(documents.isArchivedExternalSource(10L, "GITHUB", "GITHUB:repo:item-2")).thenReturn(true);

        ConnectorService service = new ConnectorService(
                List.of(adapter), repository, documents, timeline, new ObjectMapper(), props,
                googleTokens, externalOAuth, policy
        );
        User user = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");

        assertEquals(0, service.importItems(10L, "GITHUB", "repo", user));

        verify(repository, never()).saveItem(
                anyLong(), any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any()
        );
        verify(documents, never()).importExternalText(
                anyLong(), anyString(), anyString(), anyString(), anyString(), any(User.class)
        );
        verify(repository).saveSyncState(
                eq(10L), eq("GITHUB"), eq("repo"), eq("SUCCESS"),
                contains("보관 중인 자료 1건"), eq(0)
        );
    }

    @Test
    void permanentlyDeletedExternalItemIsNotStoredAgainDuringResync() {
        ReadOnlyConnector adapter = mock(ReadOnlyConnector.class);
        ConnectorRepository repository = mock(ConnectorRepository.class);
        DocumentService documents = mock(DocumentService.class);
        TimelineRepository timeline = mock(TimelineRepository.class);
        HubProperties props = mock(HubProperties.class);
        GoogleAccessTokenProvider googleTokens = mock(GoogleAccessTokenProvider.class);
        ExternalOAuthService externalOAuth = mock(ExternalOAuthService.class);
        ConnectorPolicyRepository policy = mock(ConnectorPolicyRepository.class);

        when(adapter.type()).thenReturn("GITHUB");
        when(adapter.fetch("repo", "token")).thenReturn(List.of(new ExternalContent(
                "repo:item-1", "GIT_ISSUE", "삭제한 이슈", "다시 들어오면 안 되는 내용",
                null, null, "Alice", "https://github.com/example/repo/issues/1",
                OffsetDateTime.parse("2026-09-25T00:00:00Z"), Map.of()
        )));
        when(policy.isEnabled("GITHUB")).thenReturn(true);
        when(externalOAuth.token(7L, "GITHUB")).thenReturn("token");
        when(documents.isPermanentlyDeletedExternalSource(10L, "GITHUB", "GITHUB:repo:item-1")).thenReturn(true);

        ConnectorService service = new ConnectorService(
                List.of(adapter), repository, documents, timeline, new ObjectMapper(), props,
                googleTokens, externalOAuth, policy
        );
        User user = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");

        assertEquals(0, service.importItems(10L, "GITHUB", "repo", user));

        verify(repository, never()).saveItem(
                anyLong(), any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any()
        );
        verify(documents, never()).importExternalText(
                anyLong(), anyString(), anyString(), anyString(), anyString(), any(User.class)
        );
        verify(repository).saveSyncState(
                eq(10L), eq("GITHUB"), eq("repo"), eq("SUCCESS"),
                contains("영구 삭제한 자료 1건"), eq(0)
        );
    }
}
