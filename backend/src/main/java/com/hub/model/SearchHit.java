package com.hub.model;

import java.time.OffsetDateTime;

/**
 * Searchable document chunk with document/version/source identity and lightweight metadata.
 * Metadata is intentionally carried with the hit so natural-language filters do not need N+1 lookups.
 */
public record SearchHit(
        long chunkId,
        long versionId,
        long documentId,
        int versionNo,
        String sourceType,
        String sourceIdentifier,
        String documentName,
        String paragraphRef,
        String content,
        String author,
        OffsetDateTime sourceCreatedAt
) {
    public SearchHit(long chunkId,
                     long versionId,
                     long documentId,
                     int versionNo,
                     String sourceType,
                     String sourceIdentifier,
                     String documentName,
                     String paragraphRef,
                     String content,
                     String author,
                     OffsetDateTime sourceCreatedAt) {
        this.chunkId = chunkId;
        this.versionId = versionId;
        this.documentId = documentId;
        this.versionNo = versionNo;
        this.sourceType = sourceType;
        this.sourceIdentifier = sourceIdentifier;
        this.documentName = documentName;
        this.paragraphRef = paragraphRef;
        this.content = content;
        this.author = author;
        this.sourceCreatedAt = sourceCreatedAt;
    }

    /** Backward-compatible constructor used by focused unit tests and non-metadata call sites. */
    public SearchHit(long chunkId,
                     long versionId,
                     long documentId,
                     int versionNo,
                     String sourceType,
                     String sourceIdentifier,
                     String documentName,
                     String paragraphRef,
                     String content) {
        this(chunkId, versionId, documentId, versionNo, sourceType, sourceIdentifier,
                documentName, paragraphRef, content, "", null);
    }
}
