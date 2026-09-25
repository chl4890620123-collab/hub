package com.hub.service;

import com.hub.model.User;
import com.hub.repository.DocumentRepository;
import com.hub.repository.TimelineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentServiceOwnershipTest {
    @Test
    void teammateCannotEditSomeoneElsesManualDocument() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentService service = service(documents);
        when(documents.findMeta(77L)).thenReturn(Optional.of(
                new DocumentRepository.DocumentMeta(77L, 9L, "MANUAL_TEXT", "메모", 22L, false, false)
        ));

        assertThrows(AccessDeniedException.class,
                () -> service.manualEdit(9L, 77L, "변경", "내용", member(11L)));
    }

    @Test
    void teammateCannotStartRevisionWorkflowForSomeoneElsesManualDocument() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentService service = service(documents);
        when(documents.findMeta(77L)).thenReturn(Optional.of(
                new DocumentRepository.DocumentMeta(77L, 9L, "MANUAL_TEXT", "메모", 22L, false, false)
        ));

        assertThrows(AccessDeniedException.class,
                () -> service.reviseDraftFromMeeting(9L, 77L, 88L, member(11L)));
    }

    private static DocumentService service(DocumentRepository documents) {
        return new DocumentService(
                documents,
                mock(DocumentParserService.class),
                mock(ParagraphChunker.class),
                mock(FileStorageService.class),
                mock(AiClient.class),
                mock(TimelineRepository.class),
                mock(VectorIndexService.class),
                mock(PlatformTransactionManager.class),
                mock(SensitiveDataMaskingService.class)
        );
    }

    private static User member(long id) {
        return new User(id, "member" + id, "member" + id + "@example.test", "Member " + id,
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
    }
}
