package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.model.SearchHit;
import com.hub.repository.DocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentContextServiceTest {
    @Test
    void smallDocumentUsesWholeLatestVersionAndKeepsChunkEvidenceIds() {
        DocumentRepository repo = mock(DocumentRepository.class);
        HubProperties props = properties(12000, 2, 3, 16, 30000);
        DocumentContextService service = new DocumentContextService(repo, props);
        SearchHit a = hit(11, 101, 7, 1, "PRD.docx", "1장", "로그인 구조를 분리한다.");
        SearchHit b = hit(12, 101, 7, 1, "PRD.docx", "2장", "관리자 승인 흐름을 사용한다.");
        when(repo.chunksForVersion(101)).thenReturn(List.of(a, b));
        when(repo.versionText(101)).thenReturn(a.content() + "\n" + b.content());

        var context = service.build(1L, "관리자 로그인", List.of(a), null);
        assertEquals(List.of(11L, 12L), context.stream().map(x -> x.hit().chunkId()).toList());
        assertTrue(context.stream().allMatch(DocumentContextService.ContextItem::wholeDocument));
    }

    @Test
    void largeDocumentExpandsAroundMatchingChunkInsteadOfReadingUnrelatedTail() {
        DocumentRepository repo = mock(DocumentRepository.class);
        HubProperties props = properties(20, 1, 3, 8, 10000);
        DocumentContextService service = new DocumentContextService(repo, props);
        SearchHit c0 = hit(20, 201, 8, 1, "large.docx", "p0", "alpha");
        SearchHit c1 = hit(21, 201, 8, 1, "large.docx", "p1", "beta");
        SearchHit c2 = hit(22, 201, 8, 1, "large.docx", "p2", "로그인 오류 원인");
        SearchHit c3 = hit(23, 201, 8, 1, "large.docx", "p3", "대응 방법");
        SearchHit c4 = hit(24, 201, 8, 1, "large.docx", "p4", "unrelated tail");
        when(repo.chunksForVersion(201)).thenReturn(List.of(c0,c1,c2,c3,c4));
        when(repo.versionText(201)).thenReturn("x".repeat(200));

        var context = service.build(1L, "로그인 오류", List.of(c2), null);
        assertEquals(List.of(21L,22L,23L), context.stream().map(x -> x.hit().chunkId()).toList());
        assertTrue(context.stream().noneMatch(DocumentContextService.ContextItem::wholeDocument));
    }

    private static SearchHit hit(long chunk, long version, long doc, int versionNo, String name, String ref, String text) {
        return new SearchHit(chunk, version, doc, versionNo, "FILE", "src:" + doc, name, ref, text);
    }

    private static HubProperties properties(int whole, int neighbors, int docs, int ragChunks, int ragChars) {
        return new HubProperties("./data", "http://localhost:8000", true,
            "./local-reader", "./config/search-rules.yml", whole, neighbors, docs, ragChunks, ragChars,
                "", "", "", "", "", "", "");
    }
}
