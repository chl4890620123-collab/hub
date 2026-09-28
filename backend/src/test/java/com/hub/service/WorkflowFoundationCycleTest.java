package com.hub.service;

import com.hub.repository.EvidenceRepository;
import com.hub.repository.TimelineRepository;
import com.hub.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Exercises the actual persistence transitions against an isolated example workspace. */
class WorkflowFoundationCycleTest {
    @Test
    void meetingCandidateToAdminAssignmentMemberSubmissionAndApproval() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:workflow-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE todo (
                    id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL, source_meeting_id BIGINT,
                    title VARCHAR(500), description VARCHAR(1000), assignee_id BIGINT, assignee_text VARCHAR(200),
                    assignee_suggestion_id BIGINT, assignee_suggestion_text VARCHAR(200),
                    due_date DATE, due_date_suggestion DATE, confidence VARCHAR(20),
                    review_status VARCHAR(30), task_status VARCHAR(30), assignment_status VARCHAR(40),
                    possible_duplicate_of_id BIGINT, duplicate_reason VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    google_calendar_event_id VARCHAR(200), pending_approval BOOLEAN DEFAULT FALSE,
                    status_note VARCHAR(2000), completion_url VARCHAR(2000), deleted_at TIMESTAMP, deleted_by BIGINT,
                    confirmed_by BIGINT, confirmed_at TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO todo(id,project_id,source_meeting_id,title,description,assignee_suggestion_id,
                                 review_status,task_status,assignment_status)
                VALUES(7,1,4,'회의 후 일정 공지','베타 일정 공지',5,'AI_GENERATED','TODO','ACTIVE')
                """);

        TodoRepository todos = new TodoRepository(jdbc);
        WorkflowFoundationService workflow = new WorkflowFoundationService(
                todos, mock(EvidenceRepository.class), mock(TimelineRepository.class), jdbc);

        var candidate = workflow.todo(1, 7);
        assertEquals("ADMIN_REVIEW", candidate.agent().recommendedAction());
        assertTrue(candidate.relations().stream().anyMatch(r -> r.predicate().equals("DERIVED_FROM")
                && r.to().equals("hub://projects/1/meetings/4")));
        assertFalse(candidate.relations().stream().anyMatch(r -> r.predicate().equals("ASSIGNED_TO")));

        assertTrue(todos.confirm(7, 99, 5, "박준호", null));
        var assigned = workflow.todo(1, 7);
        assertEquals("ASSIGNEE_START", assigned.agent().recommendedAction());
        assertTrue(assigned.relations().stream().anyMatch(r -> r.predicate().equals("ASSIGNED_TO")
                && r.to().equals("hub://projects/1/members/5")));

        assertTrue(todos.requireReassignment(7));
        var handoff = workflow.todo(1, 7);
        assertEquals("ADMIN_REASSIGN", handoff.agent().recommendedAction());
        assertFalse(handoff.relations().stream().anyMatch(r -> r.predicate().equals("ASSIGNED_TO")));
        assertTrue(todos.reassign(7, 6, "새 담당자"));

        assertTrue(todos.updateTaskStatus(7, "IN_PROGRESS"));
        assertEquals("ASSIGNEE_WORK_AND_SUBMIT", workflow.todo(1, 7).agent().recommendedAction());
        assertTrue(todos.requestHelp(7, "접근 권한 필요"));
        assertEquals("ASSIGNEE_RESOLVE_HELP", workflow.todo(1, 7).agent().recommendedAction());
        assertTrue(todos.resolveHelp(7));
        assertTrue(todos.requestCompletion(7, "https://example.org/report"));
        assertEquals("ADMIN_REVIEW_COMPLETION", workflow.todo(1, 7).agent().recommendedAction());
        assertFalse(todos.updateTaskStatus(7, "DONE"));
        assertTrue(todos.rejectCompletion(7, "수정 필요"));
        assertEquals("ASSIGNEE_RESUME", workflow.todo(1, 7).agent().recommendedAction());
        assertTrue(todos.updateTaskStatus(7, "IN_PROGRESS"));
        assertTrue(todos.requestCompletion(7, "https://example.org/report-v2"));
        assertTrue(todos.approveCompletion(7));
        assertEquals("NONE", workflow.todo(1, 7).agent().recommendedAction());
        assertEquals("DONE", workflow.todo(1, 7).resource().state());
        assertThrows(IllegalArgumentException.class, () -> workflow.todo(2, 7));
    }

}
