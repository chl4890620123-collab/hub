package com.hub.model;

import java.time.OffsetDateTime;

/**
 * Provider-neutral recommended search result for Hub documents and read-only connectors.
 * evidenceId is positive for Hub chunks and negative for external_item rows so RAG evidence
 * can map back to the exact source without id collisions.
 */
public record MaterialHit(
        long evidenceId,
        String sourceType,
        String sourceLabel,
        String itemType,
        String title,
        String location,
        String snippet,
        String author,
        String sourceUrl,
        OffsetDateTime sourceCreatedAt,
        int recommendationRank,
        String matchType,
        String recommendationReason
) {}
