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

    public List<Attachment> listForTodoAll(long todoId) {
        return jdbc.query(selectColumns() + " " + """
                FROM file_attachment
                WHERE todo_id=?
                ORDER BY id
                """, (rs, n) -> map(rs), todoId);
    }

    public List<Attachment> listForTodoVisible(long todoId, long userId) {
        return jdbc.query(selectColumns() + " " + """
                FROM file_attachment
                WHERE todo_id=? AND (sender_id=? OR recipient_id=?)
                ORDER BY id DESC
                """, (rs, n) -> map(rs), todoId, userId, userId);
    }

    public List<Attachment> listForUser(long projectId, long userId) {
        return jdbc.query(selectColumns() + " " + """
                 FROM file_attachment
                 WHERE project_id=? AND todo_id IS NULL
                   AND (sender_id=? OR (recipient_id=? AND recipient_hidden_at IS NULL))
                 ORDER BY id DESC LIMIT 300
                """, (rs, n) -> map(rs), projectId, userId, userId);
    }

    /** Filename/note lookup uses the same private visibility rule as download. */
    public List<Attachment> searchVisible(long projectId, long userId, String query, int limit) {
        String term = "%" + (query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT)) + "%";
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbc.query("""
                 SELECT fa.id,fa.project_id,fa.todo_id,fa.sender_id,fa.recipient_id,fa.file_name,fa.content_type,
                        fa.size_bytes,fa.storage_path,fa.note,fa.read_at,fa.created_at
                 FROM file_attachment fa
                 WHERE fa.project_id=?
                   AND (fa.sender_id=? OR (fa.recipient_id=? AND fa.recipient_hidden_at IS NULL))
                   AND (
                     fa.todo_id IS NULL
                     OR EXISTS (
                       SELECT 1 FROM todo t
                       WHERE t.id=fa.todo_id AND t.deleted_at IS NULL
                     )
                   )
                   AND (LOWER(fa.file_name) LIKE ? OR LOWER(COALESCE(fa.note,'')) LIKE ?)
                 ORDER BY fa.id DESC LIMIT ?
                """, (rs, n) -> map(rs), projectId, userId, userId, term, term, bounded);
    }

    public int reassignTodoRecipient(long todoId, long newRecipientId) {
        return jdbc.update("UPDATE file_attachment SET recipient_id=? WHERE todo_id=?", newRecipientId, todoId);
    }

    public void markRead(long id) {
        jdbc.update("UPDATE file_attachment SET read_at=CURRENT_TIMESTAMP WHERE id=? AND read_at IS NULL", id);
    }

    public boolean updateMetadata(long id, String fileName, String note) {
        return jdbc.update("UPDATE file_attachment SET file_name=?,note=? WHERE id=?", fileName, note, id) == 1;
    }

    public boolean hideForRecipient(long id, long recipientId) {
        return jdbc.update("""
                UPDATE file_attachment SET recipient_hidden_at=CURRENT_TIMESTAMP
                WHERE id=? AND todo_id IS NULL AND recipient_id=? AND recipient_hidden_at IS NULL
                """, id, recipientId) == 1;
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
