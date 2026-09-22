package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.util.HttpRequestFactories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Syncs a todo's due date to the assignee's own Google Calendar as a plain all-day event - nothing
 * more. Every call here is best-effort: the assignee may never have linked Google, may have linked it
 * before calendar.events was added to the requested scope (an old token 403s), or Calendar may just be
 * down for a moment - none of that may ever block confirming, completing, or deleting a todo, so every
 * method swallows its own failure and returns null/does nothing instead of throwing.
 */
@Service
public class GoogleCalendarService {
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    private final GoogleAccessTokenProvider tokens;
    private final RestClient client;
    private final ObjectMapper json;

    public GoogleCalendarService(GoogleAccessTokenProvider tokens, RestClient.Builder builder, ObjectMapper json) {
        this.tokens = tokens;
        this.json = json;
        this.client = builder.baseUrl("https://www.googleapis.com/calendar/v3")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(20)))
                .build();
    }

    /** Creates an all-day event for the due date. Returns the new event id, or null if it could not
     * be created (not linked, wrong scope, API error) - the caller stores whatever comes back as-is. */
    public String createEvent(long assigneeUserId, String title, String description, LocalDate dueDate) {
        if (dueDate == null || !tokens.connected(assigneeUserId)) return null;
        try {
            String token = tokens.accessToken(assigneeUserId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("summary", title);
            if (description != null && !description.isBlank()) body.put("description", description);
            body.put("start", Map.of("date", dueDate.toString()));
            body.put("end", Map.of("date", dueDate.plusDays(1).toString()));
            String response = client.post().uri("/calendars/primary/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(token))
                    .body(json.writeValueAsString(body))
                    .retrieve().body(String.class);
            JsonNode node = json.readTree(response == null ? "{}" : response);
            String id = node.path("id").asText("");
            return id.isBlank() ? null : id;
        } catch (Exception e) {
            log.info("Google Calendar event creation skipped for user {}: {}", assigneeUserId, e.getMessage());
            return null;
        }
    }

    /** No-op if eventId is null/blank or the request fails for any reason - the todo delete/status
     * change this is called from must proceed either way. */
    public void deleteEvent(long assigneeUserId, String eventId) {
        if (eventId == null || eventId.isBlank() || !tokens.connected(assigneeUserId)) return;
        try {
            String token = tokens.accessToken(assigneeUserId);
            client.delete().uri("/calendars/primary/events/{id}", eventId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.info("Google Calendar event delete skipped for user {}: {}", assigneeUserId, e.getMessage());
        } catch (Exception ignored) {
            // event already gone, or account since disconnected - either way there is nothing left to clean up
        }
    }
}
