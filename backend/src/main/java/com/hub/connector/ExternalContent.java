package com.hub.connector;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Connector-neutral content. A connector may return normalized text or a binary file.
 * Business services never need provider-specific if/else branches.
 */
public record ExternalContent(
        String externalId,
        String itemType,
        String title,
        String content,
        byte[] binaryContent,
        String contentType,
        String author,
        String sourceUrl,
        OffsetDateTime createdAt,
        Map<String, Object> metadata) {

    public boolean hasBinary() {
        return binaryContent != null && binaryContent.length > 0;
    }
}
