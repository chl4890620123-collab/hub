package com.hub.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

/** Small shared parsing helpers; provider-specific HTTP/auth semantics stay inside each connector. */
final class ConnectorSupport {
    private ConnectorSupport() {}

    static JsonNode json(ObjectMapper mapper, String body, String invalidMessage) {
        try {
            return mapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalStateException(invalidMessage, e);
        }
    }

    static OffsetDateTime date(String value) {
        try {
            return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
