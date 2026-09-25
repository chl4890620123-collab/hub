package com.hub.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAttachmentRepositoryVisibilityTest {
    private JdbcTemplate jdbc;
    private FileAttachmentRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:file-attachment-visible-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        repository = new FileAttachmentRepository(jdbc);

        jdbc.execute("CREATE TABLE todo(id BIGINT PRIMARY KEY, deleted_at TIMESTAMP)");
        jdbc.update("INSERT INTO todo(id,deleted_at) VALUES(77,NULL)");
        jdbc.execute("""
                CREATE TABLE file_attachment(
                  id BIGINT PRIMARY KEY,
                  project_id BIGINT NOT NULL,
                  todo_id BIGINT,
                  sender_id BIGINT NOT NULL,
                  recipient_id BIGINT,
                  file_name VARCHAR(500) NOT NULL,
                  content_type VARCHAR(200),
                  size_bytes BIGINT NOT NULL,
                  storage_path VARCHAR(2000) NOT NULL,
                  note VARCHAR(1000),
                  read_at TIMESTAMP,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void todoAttachmentListIsVisibleOnlyToSenderAndRecipient() {
        insert(1L, 9L, 77L, 11L, 22L, "private-plan.txt", "secret plan");

        assertEquals(List.of(1L), repository.listForTodoVisible(77L, 11L).stream().map(FileAttachmentRepository.Attachment::id).toList());
        assertEquals(List.of(1L), repository.listForTodoVisible(77L, 22L).stream().map(FileAttachmentRepository.Attachment::id).toList());
        assertTrue(repository.listForTodoVisible(77L, 33L).isEmpty());
    }

    @Test
    void reassignmentMovesRecipientVisibilityToNewAssignee() {
        insert(1L, 9L, 77L, 11L, 22L, "handoff.txt", "handoff");

        assertEquals(1, repository.reassignTodoRecipient(77L, 33L));

        assertTrue(repository.listForTodoVisible(77L, 22L).isEmpty());
        assertEquals(List.of(1L), repository.listForTodoVisible(77L, 33L).stream()
                .map(FileAttachmentRepository.Attachment::id).toList());
        assertEquals(List.of(1L), repository.listForTodoVisible(77L, 11L).stream()
                .map(FileAttachmentRepository.Attachment::id).toList());
    }

    @Test
    void trashedTodoAttachmentStaysInTrashButDisappearsFromActiveSearch() {
        insert(1L, 9L, 77L, 11L, 22L, "private-plan.txt", "secret plan");
        jdbc.update("UPDATE todo SET deleted_at=CURRENT_TIMESTAMP WHERE id=77");

        assertEquals(List.of(1L), repository.listForTodoVisible(77L, 22L).stream()
                .map(FileAttachmentRepository.Attachment::id).toList());
        assertTrue(repository.searchVisible(9L, 22L, "secret", 20).isEmpty());
    }

    @Test
    void attachmentSearchCannotLeakPrivateTodoFileToOtherProjectMember() {
        insert(1L, 9L, 77L, 11L, 22L, "private-plan.txt", "secret plan");
        insert(2L, 9L, null, 33L, 44L, "other-secret.txt", "secret note");

        assertEquals(List.of(1L), repository.searchVisible(9L, 22L, "secret", 20).stream()
                .map(FileAttachmentRepository.Attachment::id).toList());
        assertTrue(repository.searchVisible(9L, 55L, "secret", 20).isEmpty());
    }

    private void insert(long id, long projectId, Long todoId, long senderId, Long recipientId, String fileName, String note) {
        jdbc.update("""
                INSERT INTO file_attachment(id,project_id,todo_id,sender_id,recipient_id,file_name,content_type,size_bytes,storage_path,note)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, id, projectId, todoId, senderId, recipientId, fileName, "text/plain", 4L, "/trusted/" + fileName, note);
    }
}
