package com.hub.service;

import com.hub.model.TodoItem;
import com.hub.repository.EvidenceRepository;
import com.hub.repository.TimelineRepository;
import com.hub.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowFoundationServiceTest {
    private final TodoRepository todos = mock(TodoRepository.class);
    private final EvidenceRepository evidence = mock(EvidenceRepository.class);
    private final TimelineRepository timeline = mock(TimelineRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkflowFoundationService service = new WorkflowFoundationService(todos, evidence, timeline, jdbc);

    @Test
    void projectsCannotReadEachOthersTodos() {
        when(todos.find(7)).thenReturn(todo("CONFIRMED", false));
        assertThrows(IllegalArgumentException.class, () -> service.todo(2, 7));
        verifyNoInteractions(evidence, timeline, jdbc);
    }

    @Test
    void meetingCandidateThenAssigneeSubmissionAndAdminApprovalAreRepresentedWithoutMutation() {
        when(todos.find(7)).thenReturn(todo("AI_GENERATED", false));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(7L), eq(1L)))
                .thenReturn(List.of(4L));
        when(evidence.forTodo(7)).thenReturn(List.of(new EvidenceRepository.EvidenceView(
                9, 3L, null, "회의 근거", null, "회의록", null, null, null, null, null, null)));
        var candidate = service.todo(1, 7);
        assertEquals("hub://projects/1/todos/7", candidate.resource().uri());
        assertEquals("ADMIN_REVIEW", candidate.agent().recommendedAction());
        assertTrue(candidate.relations().stream().anyMatch(r -> r.predicate().equals("DERIVED_FROM") && r.to().endsWith("/meetings/4")));
        assertEquals("회의 근거", candidate.knowledge().get(0).excerpt());

        var assigned = WorkflowFoundationService.next(todo("CONFIRMED", false));
        assertEquals("ASSIGNEE_START", assigned.recommendedAction());
        assertEquals("ADMIN_REVIEW_COMPLETION", WorkflowFoundationService.next(todo("CONFIRMED", true)).recommendedAction());
        verify(todos, never()).approveCompletion(anyLong());
    }

    private static TodoItem todo(String review, boolean pending) {
        return new TodoItem(7, 1, "업무", "설명", 5L, "담당자", null, null, null, null,
                null, review, "TODO", "ACTIVE", null, null, LocalDateTime.now(), LocalDateTime.now(),
                null, pending, null, null, null, null);
    }
}
