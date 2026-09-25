package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.model.User;
import com.hub.repository.ProjectRepository;
import com.hub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OAuthAccessRecheckTest {
    @Test
    void externalOAuthCallbackRejectsUserRemovedFromProjectBeforeTokenExchange() {
        HubProperties props = mock(HubProperties.class);
        when(props.githubClientId()).thenReturn("client");
        when(props.githubClientSecret()).thenReturn("secret");
        ExternalOAuthTokenStore store = mock(ExternalOAuthTokenStore.class);
        UserRepository users = mock(UserRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        User user = member(7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(projects.canAccess(10L, 7L, false)).thenReturn(false);

        ExternalOAuthService service = new ExternalOAuthService(
                props, store, RestClient.builder(), new ObjectMapper(), users, projects
        );
        String url = service.authorize(7L, 10L, "GITHUB", "https://example.test/callback");
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");

        assertThrows(IllegalStateException.class, () -> service.callback("code", state));

        verify(store, never()).put(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void googleOAuthCallbackRejectsInactiveUserBeforeTokenExchange() {
        HubProperties props = mock(HubProperties.class);
        when(props.googleClientId()).thenReturn("client");
        when(props.googleClientSecret()).thenReturn("secret");
        GoogleAccessTokenProvider tokens = mock(GoogleAccessTokenProvider.class);
        UserRepository users = mock(UserRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        User suspended = new User(7L, "member7", "member7@example.test", "Member 7",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "SUSPENDED", false, "APPROVED");
        when(users.findById(7L)).thenReturn(Optional.of(suspended));

        GoogleOAuthService service = new GoogleOAuthService(
                props, tokens, RestClient.builder(), new ObjectMapper(), users, projects
        );
        String url = service.authorizationUrl(7L, 10L, "https://example.test/callback");
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");

        assertThrows(IllegalStateException.class, () -> service.consume(state, "code"));

        verify(tokens, never()).connect(anyLong(), anyLong(), anyString(), anyString(), anyLong(), any());
    }

    private static User member(long id) {
        return new User(id, "member" + id, "member" + id + "@example.test", "Member " + id,
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
    }
}
