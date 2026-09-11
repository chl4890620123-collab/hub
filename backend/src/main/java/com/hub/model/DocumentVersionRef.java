package com.hub.model;

/** Latest active document version selected by filename/title rules before RAG context expansion. */
public record DocumentVersionRef(
        long documentId,
        long versionId,
        int versionNo,
        String documentName,
        String sourceType,
        String sourceIdentifier,
        String fullText
) {}
