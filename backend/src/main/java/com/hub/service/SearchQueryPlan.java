package com.hub.service;

import java.time.OffsetDateTime;
import java.util.Set;

/** Parsed search intent. It changes retrieval weights without replacing the existing hybrid search engines. */
public record SearchQueryPlan(
        String originalQuery,
        String searchText,
        Intent intent,
        String exactFilename,
        String author,
        OffsetDateTime fromInclusive,
        OffsetDateTime toExclusive,
        Set<String> sourceTypes,
        double ruleWeight,
        double templateWeight,
        double semanticWeight,
        double lexicalWeight,
        double metadataWeight
) {
    public enum Intent { EXACT_FILE, TEMPLATE, FILTERED, QUESTION, GENERAL }

    public SearchQueryPlan {
        sourceTypes = sourceTypes == null ? Set.of() : Set.copyOf(sourceTypes);
        exactFilename = exactFilename == null ? "" : exactFilename;
        author = author == null ? "" : author;
        searchText = searchText == null || searchText.isBlank() ? originalQuery : searchText;
    }

    public boolean hasMetadataFilters() {
        return !author.isBlank() || fromInclusive != null || toExclusive != null || !sourceTypes.isEmpty();
    }

    public boolean hasDateFilter() {
        return fromInclusive != null || toExclusive != null;
    }
}
