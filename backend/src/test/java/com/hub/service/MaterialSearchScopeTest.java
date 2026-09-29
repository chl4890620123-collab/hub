package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.dto.AiDtos;
import com.hub.model.DocumentVersionRef;
import com.hub.model.SearchHit;
import com.hub.repository.ConnectorRepository;
import com.hub.repository.DocumentRepository;
import com.hub.repository.FileAttachmentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaterialSearchScopeTest {
    private static final long PROJECT = 7L;
    private static final String QUESTION = "기획서 승인 내용";

    @Test
    void managedRuleLimitsAnswerEvidenceWhileGeneralSearchStillFindsOtherFiles() {
        Fixture f = new Fixture();
        SearchHit allowed = hit(11, 101, 1, "2026-PRD.docx", "승인 내용");
        SearchHit unrelated = hit(22, 202, 2, "회의록.docx", "승인 내용");
        f.rule(true, "양식.docx");
        when(f.context.matchingLatestDocuments(eq(PROJECT), eq(List.of("*PRD*")), anyInt()))
                .thenReturn(List.of(ref(allowed)));
        when(f.context.chunks(101)).thenReturn(List.of(allowed));
        when(f.lexical.searchNative(PROJECT, QUESTION, 100)).thenReturn(List.of(unrelated));
        when(f.templates.similar(PROJECT, "양식.docx", 40))
                .thenReturn(List.of(new TemplateSimilarityService.TemplateMatch(unrelated, 1)));
        // A pinned/context expansion must not bypass the rule's document boundary.
        when(f.context.build(eq(PROJECT), eq(QUESTION), anyList(), any()))
                .thenReturn(List.of(item(allowed), item(unrelated)));
        when(f.ai.rag(eq(QUESTION), anyList())).thenReturn(new AiDtos.RagResponse(
                "승인됨", List.of(new AiDtos.RagEvidence(11, "승인 내용"))));

        var answer = f.service.ask(PROJECT, QUESTION);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiDtos.RagChunk>> sent = ArgumentCaptor.forClass(List.class);
        verify(f.ai).rag(eq(QUESTION), sent.capture());
        assertEquals(List.of(11L), sent.getValue().stream().map(AiDtos.RagChunk::id).toList());
        assertEquals(List.of(11L), answer.sources().stream().map(x -> x.evidenceId()).toList());

        var search = f.service.search(PROJECT, QUESTION);
        assertTrue(search.stream().anyMatch(hit -> hit.title().equals("회의록.docx")));
        assertTrue(search.stream().anyMatch(hit -> hit.matchType().equals("원본 양식과 비슷한 자료")));
    }

    @Test
    void managedRuleWithNoMatchingFilesAbstainsButDoesNotHideSearchResults() {
        Fixture f = new Fixture();
        f.rule(true);
        when(f.lexical.searchNative(PROJECT, QUESTION, 100))
                .thenReturn(List.of(hit(22, 202, 2, "회의록.docx", "승인 내용")));

        var answer = f.service.ask(PROJECT, QUESTION);
        assertEquals("관련 자료에서 해당 내용을 확인하지 못했습니다.", answer.answer());
        assertTrue(answer.sources().isEmpty());
        verify(f.ai, never()).rag(anyString(), anyList());
        assertTrue(f.service.search(PROJECT, QUESTION).stream()
                .anyMatch(hit -> hit.title().equals("회의록.docx")));
    }

    @Test
    void bundledFallbackRuleKeepsBroadAnswerContext() {
        Fixture f = new Fixture();
        f.rule(false);
        SearchHit other = hit(22, 202, 2, "회의록.docx", "승인 내용");
        when(f.lexical.searchNative(PROJECT, QUESTION, 100)).thenReturn(List.of(other));
        when(f.context.build(eq(PROJECT), eq(QUESTION), anyList(), any())).thenReturn(List.of(item(other)));
        when(f.ai.rag(eq(QUESTION), anyList())).thenReturn(new AiDtos.RagResponse(
                "승인됨", List.of(new AiDtos.RagEvidence(22, "승인 내용"))));

        assertEquals(22L, f.service.ask(PROJECT, QUESTION).sources().get(0).evidenceId());
    }

    private static SearchHit hit(long chunk, long version, long doc, String name, String text) {
        return new SearchHit(chunk, version, doc, 1, "FILE", "source:" + doc, name, "1장", text);
    }

    private static DocumentVersionRef ref(SearchHit hit) {
        return new DocumentVersionRef(hit.documentId(), hit.versionId(), 1,
                hit.documentName(), hit.sourceType(), hit.sourceIdentifier(), hit.content());
    }

    private static DocumentContextService.ContextItem item(SearchHit hit) {
        return new DocumentContextService.ContextItem(hit, hit.content(), hit.documentName(), false, false);
    }

    private static final class Fixture {
        final ConnectorRepository connectors = mock(ConnectorRepository.class);
        final DocumentRepository documents = mock(DocumentRepository.class);
        final FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        final LexicalSearchService lexical = mock(LexicalSearchService.class);
        final AiClient ai = mock(AiClient.class);
        final VectorIndexService vectors = mock(VectorIndexService.class);
        final SearchRuleService rules = mock(SearchRuleService.class);
        final DocumentContextService context = mock(DocumentContextService.class);
        final TemplateSimilarityService templates = mock(TemplateSimilarityService.class);
        final SearchQueryRouter router = mock(SearchQueryRouter.class);
        final SensitiveDataMaskingService masking = mock(SensitiveDataMaskingService.class);
        final MaterialSearchService service;

        Fixture() {
            HubProperties props = new HubProperties("./data", "http://localhost:8000", true,
                    "./local-reader", "./config/search-rules.yml", 12000, 2, 10, 16, 30000,
                    "", "", "", "", "", "", "");
            when(router.route(QUESTION)).thenReturn(new SearchQueryPlan(QUESTION, QUESTION,
                    SearchQueryPlan.Intent.GENERAL, "", "", null, null, Set.of(), 1, 1, 1, 1, 1));
            service = new MaterialSearchService(connectors, documents, attachments, lexical, ai,
                    vectors, new ObjectMapper(), rules, context, templates, router, props, masking);
        }

        void rule(boolean managed) {
            rule(managed, "");
        }

        void rule(boolean managed, String targetFile) {
            when(rules.match(PROJECT, QUESTION)).thenReturn(Optional.of(new SearchRuleService.RuleMatch(
                    managed ? 1L : null, "기획서", List.of("*PRD*"), "smart", "기획서", targetFile, 1, managed)));
        }
    }
}
