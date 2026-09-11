package com.hub.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParagraphChunkerTest {
    private final ParagraphChunker chunker = new ParagraphChunker();

    @Test
    void keepsParagraphsAndSplitsLongContent() {
        List<String> chunks = chunker.chunk("첫 문단입니다.\n\n두 번째 문단입니다.");
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("첫 문단"));
    }

    @Test
    void neverReturnsEmptyChunkForBlankInput() {
        assertTrue(chunker.chunk("   ").isEmpty());
    }
}
