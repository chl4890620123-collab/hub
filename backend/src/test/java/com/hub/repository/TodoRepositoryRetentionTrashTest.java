package com.hub.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TodoRepositoryRetentionTrashTest {
    private JdbcTemplate jdbc;
    private TodoRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:todo-retention-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        repository = new TodoRepository(jdbc);

        jdbc.execute("""
                CREATE TABLE todo(
                  id BIGINT PRIMARY KEY,
                  task_status VARCHAR(30) NOT NULL,
                  review_status VARCHAR(30) NOT NULL,
                  due_date DATE,
                  deleted_at TIMESTAMP,
                  deleted_by BIGINT,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void oldCompletedTodosMoveToTrashInsteadOfBeingDeleted() {
        jdbc.update("INSERT INTO todo(id,task_status,review_status,due_date) VALUES(1,'DONE','CONFIRMED',?)",
                java.sql.Date.valueOf(LocalDate.of(2024,1,1)));
        jdbc.update("INSERT INTO todo(id,task_status,review_status,due_date) VALUES(2,'IN_PROGRESS','CONFIRMED',?)",
                java.sql.Date.valueOf(LocalDate.of(2024,1,1)));

        assertEquals(1, repository.moveCompletedToTrashOlderThan(LocalDate.of(2025,1,1)));

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM todo", Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM todo WHERE id=1", java.sql.Timestamp.class));
        assertNull(jdbc.queryForObject("SELECT deleted_by FROM todo WHERE id=1", Long.class));
        assertNull(jdbc.queryForObject("SELECT deleted_at FROM todo WHERE id=2", java.sql.Timestamp.class));
    }
}
