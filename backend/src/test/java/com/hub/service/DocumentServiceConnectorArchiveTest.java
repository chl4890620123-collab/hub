package com.hub.service;

import com.hub.model.User;
import com.hub.repository.DocumentRepository;
import com.hub.repository.TimelineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceConnectorArchiveTest {
    @Test
    void connectorRefreshUpdatesArchivedDocumentWithoutRestoringIt() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentParserService parser = mock(DocumentParserService.class);
        ParagraphChunker chunker = mock(ParagraphChunker.class);
        FileStorageService storage = mock(FileStorageService.class);
        AiClient ai = mock(AiClient.class);
        TimelineRepository timeline = mock(TimelineRepository.class);
        VectorIndexService vectors = mock(VectorIndexService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        SensitiveDataMaskingService piiMasking = mock(SensitiveDataMaskingService.class);

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(documents.findDocumentId(10L, "GITHUB", "GITHUB:repo:item-1")).thenReturn(Optional.of(50L));
        when(documents.latestVersion(50L)).thenReturn(Optional.empty());
        when(piiMasking.mask("새 외부 내용")).thenReturn("새 외부 내용");
        when(chunker.chunk("새 외부 내용")).thenReturn(List.of("새 외부 내용"));
        when(ai.embed(any(), anyString())).thenThrow(new IllegalStateException("embedding unavailable"));
        when(documents.createVersion(anyLong(), anyString(), anyString(), anyString())).thenReturn(60L);
        when(documents.createChunk(anyLong(), any(Integer.class), anyString(), anyString())).thenReturn(70L);

        DocumentService service = new DocumentService(
                documents, parser, chunker, storage, ai, timeline, vectors, transactionManager, piiMasking);

        service.importExternalText(
                10L, "GITHUB", "GITHUB:repo:item-1", "Archived issue", "새 외부 내용", member(7L));

        verify(documents).updateSourceMetadataPreservingArchive(50L, "Archived issue", null);
        verify(documents, never()).updateSourceMetadata(anyLong(), anyString(), any());
    }

    private static User member(long id) {
        return new User(id, "member" + id, "member" + id + "@example.test", "Member " + id,
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
    }
}
