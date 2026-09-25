package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.TodoItem;
import com.hub.model.User;
import com.hub.repository.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TodoServicePermanentDeleteAttachmentTest {
    @Test
    void permanentDeleteRemovesTodoAttachmentBytesAndMetadataFirst() throws Exception {
        TodoRepository todos = mock(TodoRepository.class);
        FeedbackRepository feedback = mock(FeedbackRepository.class);
        RevisionRepository revisions = mock(RevisionRepository.class);
        TimelineRepository timeline = mock(TimelineRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        UserRepository users = mock(UserRepository.class);
        EvidenceRepository evidence = mock(EvidenceRepository.class);
        GoogleCalendarService calendar = mock(GoogleCalendarService.class);
        FileAttachmentRepository attachments = mock(FileAttachmentRepository.class);
        FileStorageService storage = mock(FileStorageService.class);

        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        TodoService service = new TodoService(todos, feedback, revisions, timeline, projects, users, evidence,
                json, calendar, attachments, storage);

        TodoItem todo = new TodoItem(77L, 9L, "삭제할 업무", null, 22L, "담당자",
                null, null, null, null, "HIGH", "CONFIRMED", "DONE", "ACTIVE",
                null, null, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1),
                null, false, null, LocalDateTime.now(), 11L);
        User actor = new User(11L, "member11", "member11@example.test", "Member 11",
                "Hub", "Dev", "Team", "Engineer", "MEMBER", "ACTIVE", false, "APPROVED");
        FileAttachmentRepository.Attachment attachment = new FileAttachmentRepository.Attachment(
                501L, 9L, 77L, 11L, 22L, "brief.txt", "text/plain", 4L,
                "/trusted/brief.txt", null, null, LocalDateTime.now()
        );

        when(attachments.listForTodoAll(77L)).thenReturn(List.of(attachment));
        when(attachments.delete(501L)).thenReturn(true);
        when(todos.permanentDelete(77L)).thenReturn(true);

        service.permanentDelete(todo, actor);

        verify(storage).deleteStrict("/trusted/brief.txt");
        verify(attachments).delete(501L);
        verify(todos).permanentDelete(77L);
    }
}
