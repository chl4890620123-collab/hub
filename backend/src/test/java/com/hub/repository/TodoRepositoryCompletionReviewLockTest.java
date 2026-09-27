package com.hub.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

class TodoRepositoryCompletionReviewLockTest {
    private JdbcTemplate jdbc;
    private TodoRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:todo-review-lock-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        repository = new TodoRepository(jdbc);

        jdbc.execute("""
                CREATE TABLE todo(
                  id BIGINT PRIMARY KEY,
                  review_status VARCHAR(30) NOT NULL,
                  assignment_status VARCHAR(40) NOT NULL,
                  task_status VARCHAR(30) NOT NULL,
                  pending_approval BOOLEAN NOT NULL DEFAULT FALSE,
                  status_note VARCHAR(2000),
                  completion_url VARCHAR(2000),
                  deleted_at TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void pendingCompletionCannotChangeStatusHoldOrAskForHelp() {
        jdbc.update("""
                INSERT INTO todo(id,review_status,assignment_status,task_status,pending_approval,completion_url)
                VALUES(1,'CONFIRMED','ACTIVE','IN_PROGRESS',TRUE,'https://example.com/submitted')
                """);

        assertFalse(repository.updateTaskStatus(1L, "TODO"));
        assertFalse(repository.hold(1L));
        assertFalse(repository.requestHelp(1L, "도움 필요"));

        assertTrue(jdbc.queryForObject("SELECT pending_approval FROM todo WHERE id=1", Boolean.class));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT task_status FROM todo WHERE id=1", String.class));
        assertEquals("https://example.com/submitted", jdbc.queryForObject("SELECT completion_url FROM todo WHERE id=1", String.class));
    }

    @Test
    void resumingAfterRejectionClearsOldSubmissionUrl() {
        jdbc.update("""
                INSERT INTO todo(id,review_status,assignment_status,task_status,pending_approval,status_note,completion_url)
                VALUES(2,'CONFIRMED','ACTIVE','IN_PROGRESS',FALSE,'수정 필요','https://example.com/rejected')
                """);

        assertTrue(repository.updateTaskStatus(2L, "IN_PROGRESS"));

        assertNull(jdbc.queryForObject("SELECT status_note FROM todo WHERE id=2", String.class));
        assertNull(jdbc.queryForObject("SELECT completion_url FROM todo WHERE id=2", String.class));
    }

    @Test
    void askingForHelpClearsStaleRejectedSubmissionUrl() {
        jdbc.update("""
                INSERT INTO todo(id,review_status,assignment_status,task_status,pending_approval,status_note,completion_url)
                VALUES(3,'CONFIRMED','ACTIVE','IN_PROGRESS',FALSE,'수정 필요','https://example.com/rejected')
                """);

        assertTrue(repository.requestHelp(3L, "접근 권한이 필요합니다."));

        assertEquals("BLOCKED", jdbc.queryForObject("SELECT task_status FROM todo WHERE id=3", String.class));
        assertEquals("접근 권한이 필요합니다.", jdbc.queryForObject("SELECT status_note FROM todo WHERE id=3", String.class));
        assertNull(jdbc.queryForObject("SELECT completion_url FROM todo WHERE id=3", String.class));
    }
}
