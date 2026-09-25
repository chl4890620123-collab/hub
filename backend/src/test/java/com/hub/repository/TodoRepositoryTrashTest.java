package com.hub.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoRepositoryTrashTest {
    private JdbcTemplate jdbc;
    private TodoRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:todo-trash-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        repository = new TodoRepository(jdbc);

        jdbc.execute("""
                CREATE TABLE todo(
                  id BIGINT PRIMARY KEY,
                  possible_duplicate_of_id BIGINT,
                  deleted_at TIMESTAMP,
                  deleted_by BIGINT,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void permanentDeleteOnlyRemovesTrashedTodoAndDetachesDuplicateReferences() {
        jdbc.update("INSERT INTO todo(id,deleted_at,deleted_by) VALUES(1,CURRENT_TIMESTAMP,7)");
        jdbc.update("INSERT INTO todo(id,possible_duplicate_of_id) VALUES(2,1)");

        assertTrue(repository.permanentDelete(1L));
        assertEquals(0, countWhere("id=1"));
        assertNull(jdbc.queryForObject("SELECT possible_duplicate_of_id FROM todo WHERE id=2", Long.class));
    }

    @Test
    void activeTodoCannotBePermanentlyDeleted() {
        jdbc.update("INSERT INTO todo(id) VALUES(3)");

        assertFalse(repository.permanentDelete(3L));
        assertEquals(1, countWhere("id=3"));
    }

    @Test
    void restoreClearsTrashMetadata() {
        jdbc.update("INSERT INTO todo(id,deleted_at,deleted_by) VALUES(4,CURRENT_TIMESTAMP,9)");

        assertTrue(repository.restore(4L));
        assertNull(jdbc.queryForObject("SELECT deleted_at FROM todo WHERE id=4", java.sql.Timestamp.class));
        assertNull(jdbc.queryForObject("SELECT deleted_by FROM todo WHERE id=4", Long.class));
    }

    private int countWhere(String predicate) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE " + predicate, Integer.class);
    }
}
