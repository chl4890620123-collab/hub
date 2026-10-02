package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.dto.AiDtos;
import com.hub.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalysisResummaryTest {
    @Test
    void explicitResummaryCallsAiAndKeepsExistingCandidates() throws Exception {
        var f = new Fixture();
        when(f.ai.analyze(anyString(), any(), anyList()))
                .thenReturn(new AiDtos.AnalyzeResponse("Fresh summary", List.of(), List.of()));
        var result = f.service.analyzeDocument(1L, 2L, null, true);
        assertEquals("Fresh summary", result.summary());
        verify(f.ai).analyze(eq("Original text"), any(), anyList());
        verify(f.documents).updateSummary(2L, "Fresh summary");
        verify(f.runs).saveDocument(eq(1L), eq(2L), eq("DOCUMENT_ANALYSIS"), contains("Fresh summary"));
        verifyNoInteractions(f.todos, f.decisions, f.evidence);
    }

    @Test
    void ordinaryRetryUsesCachedResultWithoutAnotherAiCall() throws Exception {
        var f = new Fixture();
        assertEquals("Cached summary", f.service.analyzeDocument(1L, 2L, null).summary());
        verifyNoInteractions(f.ai);
        verify(f.documents, never()).updateSummary(anyLong(), anyString());
    }

    @Test
    void failedResummaryPreservesStoredSummaryAndWorkflow() throws Exception {
        var f = new Fixture();
        when(f.ai.analyze(anyString(), any(), anyList())).thenThrow(new IllegalStateException("503"));
        assertThrows(IllegalStateException.class, () -> f.service.analyzeDocument(1L, 2L, null, true));
        verify(f.documents, never()).updateSummary(anyLong(), anyString());
        verify(f.runs, never()).saveDocument(anyLong(), anyLong(), anyString(), anyString());
        verifyNoInteractions(f.todos, f.decisions, f.evidence);
    }

    static class Fixture {
        final AiClient ai = mock(AiClient.class);
        final DocumentRepository documents = mock(DocumentRepository.class);
        final TodoRepository todos = mock(TodoRepository.class);
        final DecisionRepository decisions = mock(DecisionRepository.class);
        final EvidenceRepository evidence = mock(EvidenceRepository.class);
        final AiRunRepository runs = mock(AiRunRepository.class);
        final AnalysisService service;
        Fixture() throws Exception {
            var mapper = new ObjectMapper();
            when(runs.successfulDocument(2L, "DOCUMENT_ANALYSIS")).thenReturn(Optional.of(
                    mapper.writeValueAsString(new AiDtos.AnalyzeResponse("Cached summary", List.of(), List.of()))));
            when(documents.versionText(2L)).thenReturn("Original text");
            var projects = mock(ProjectRepository.class);
            when(projects.listMembers(1L)).thenReturn(List.of());
            var tx = mock(PlatformTransactionManager.class);
            when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            service = new AnalysisService(ai, documents, todos, mock(TodoDuplicateDetector.class), decisions,
                    evidence, mock(TimelineRepository.class), mock(MeetingRepository.class), projects, runs, mapper, tx);
        }
    }
}
