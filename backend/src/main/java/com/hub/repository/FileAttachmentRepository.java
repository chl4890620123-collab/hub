package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * A file someone attached to a todo, or sent directly to a teammate. Metadata only - the bytes live
 * under HUB_STORAGE_ROOT via FileStorageService, exactly like document uploads.
 */
@Repository
public class FileAttachmentRepository {
    private final JdbcTemplate jdbc;
    public FileAttachmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Attachment(long id, long projectId, Long todoId, long senderId, Long recipientId,
                             String fileName, String contentType, long sizeBytes, String storagePath,
                             String note, LocalDateTime readAt, LocalDateTime createdAt) {}

    public long create(long projectId, Long todoId, long senderId, Long recipientId, String fileName,
                       String contentType, long sizeBytes, String storagePath, String note) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO file_attachment(project_id,todo_id,sender_id,recipient_id,file_name,content_type,size_bytes,storage_path,note)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            ps.setLong(1, projectId);
            if (todoId == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, todoId);
            ps.setLong(3, senderId);
            if (recipientId == null) ps.setNull(4, java.sql.Types.BIGINT); else ps.setLong(4, recipientId);
            ps.setString(5, fileName); ps.setString(6, contentType); ps.setLong(7, sizeBytes);
            ps.setString(8, storagePath); ps.setString(9, note);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Attachment id was not generated");
        return key.getKey().longValue();
    }

    public Optional<Attachment> find(long id) {
        return jdbc.query(selectColumns() + " FROM file_attachment WHERE id=?", (rs, n) -> map(rs), id)
                .stream().findFirst();
    }

    /** Internal lifecycle lookup used before permanent todo deletion; never exposed directly to clients. */
    public List<Attachment> listForTodoAll(long todoId) {
        return jdbc.query(selectColumns() + " " + """
                FROM file_attachment
                WHERE todo_id=?
                ORDER BY id
                """, (rs, n) -> map(rs), todoId);
    }

    /** Todo attachments are private to the sender and the assignee captured as recipient_id. */
    public List<Attachment> listForTodoVisible(long todoId, long userId) {
        return jdbc.query(selectColumns() + " " + """
                FROM file_attachment
                WHERE todo_id=? AND (sender_id=? OR recipient_id=?)
                ORDER BY id DESC
                """, (rs, n) -> map(rs), todoId, userId, userId);
    }

    /** Sent or received within the project - a small personal inbox, newest first. */
    public List<Attachment> listForUser(long projectId, long userId) {
        return jdbc.query(selectColumns() + " " + """
                 FROM file_attachment WHERE project_id=? AND todo_id IS NULL AND (sender_id=? OR recipient_id=?) ORDER BY id DESC LIMIT 300
                """, (rs, n) -> map(rs), projectId, userId, userId);
    }

    /** Filename/note lookup uses the same private visibility rule as download:
     * only the sender or the explicitly recorded recipient may discover the file. */
    public List<Attachment> searchVisible(long projectId, long userId, String query, int limit) {
        String term = "%" + (query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT)) + "%";
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbc.query(selectColumns() + " " + """
                 FROM file_attachment
                 WHERE project_id=?
                   AND (sender_id=? OR recipient_id=?)
                   AND (
                     todo_id IS NULL
                     OR EXISTS (
                       SELECT 1 FROM todo t
                       WHERE t.id=file_attachment.todo_id AND t.deleted_at IS NULL
                     )
                   )
                   AND (LOWER(file_name) LIKE ? OR LOWER(COALESCE(note,'')) LIKE ?)
                 ORDER BY id DESC LIMIT ?
                """, (rs, n) -> map(rs), projectId, userId, userId, term, term, bounded);
    }

    /** Move private todo files to the new assignee when ownership of the todo changes. */
    public int reassignTodoRecipient(long todoId, long newRecipientId) {
        return jdbc.update("UPDATE file_attachment SET recipient_id=? WHERE todo_id=?", newRecipientId, todoId);
    }

    public void markRead(long id) {
        jdbc.update("UPDATE file_attachment SET read_at=CURRENT_TIMESTAMP WHERE id=? AND read_at IS NULL", id);
    }

    public boolean updateMetadata(long id, String fileName, String note) {
        return jdbc.update("UPDATE file_attachment SET file_name=?,note=? WHERE id=?", fileName, note, id) == 1;
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM file_attachment WHERE id=?", id) == 1;
    }

    private static String selectColumns() {
        return "SELECT id,project_id,todo_id,sender_id,recipient_id,file_name,content_type,size_bytes,storage_path,note,read_at,created_at";
    }

    private static Attachment map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long todo = rs.getLong("todo_id"); Long todoId = rs.wasNull() ? null : todo;
        long recipient = rs.getLong("recipient_id"); Long recipientId = rs.wasNull() ? null : recipient;
        Timestamp readAt = rs.getTimestamp("read_at");
        return new Attachment(rs.getLong("id"), rs.getLong("project_id"), todoId, rs.getLong("sender_id"), recipientId,
                rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"), rs.getString("storage_path"),
                rs.getString("note"), readAt == null ? null : readAt.toLocalDateTime(), rs.getTimestamp("created_at").toLocalDateTime());
    }
}
