package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AdminSubmissionRepository {
    private final JdbcTemplate jdbc;

    public AdminSubmissionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Submission(
            long id,
            long projectId,
            long senderId,
            String title,
            String note,
            String externalUrl,
            String fileName,
            String contentType,
            Long sizeBytes,
            String storagePath,
            LocalDateTime createdAt
    ) {}

    public long create(long projectId, long senderId, String title, String note, String externalUrl,
                       String fileName, String contentType, Long sizeBytes, String storagePath) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO admin_submission(project_id,sender_id,title,note,external_url,file_name,content_type,size_bytes,storage_path)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            ps.setLong(1, projectId);
            ps.setLong(2, senderId);
            ps.setString(3, title);
            ps.setString(4, note);
            ps.setString(5, externalUrl);
            ps.setString(6, fileName);
            ps.setString(7, contentType);
            if (sizeBytes == null) ps.setNull(8, java.sql.Types.BIGINT); else ps.setLong(8, sizeBytes);
            ps.setString(9, storagePath);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Submission id was not generated");
        return key.getKey().longValue();
    }

    public Optional<Submission> find(long id) {
        return jdbc.query(selectColumns() + " FROM admin_submission WHERE id=?", (rs, n) -> map(rs), id)
                .stream().findFirst();
    }

    public List<Submission> listForProject(long projectId) {
        return jdbc.query(selectColumns() + " FROM admin_submission WHERE project_id=? ORDER BY id DESC LIMIT 500",
                (rs, n) -> map(rs), projectId);
    }

    public List<Submission> listForSender(long projectId, long senderId) {
        return jdbc.query(selectColumns() + " FROM admin_submission WHERE project_id=? AND sender_id=? ORDER BY id DESC LIMIT 500",
                (rs, n) -> map(rs), projectId, senderId);
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM admin_submission WHERE id=?", id) == 1;
    }

    private static String selectColumns() {
        return "SELECT id,project_id,sender_id,title,note,external_url,file_name,content_type,size_bytes,storage_path,created_at";
    }

    private static Submission map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long size = rs.getLong("size_bytes");
        Long sizeBytes = rs.wasNull() ? null : size;
        return new Submission(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getLong("sender_id"),
                rs.getString("title"),
                rs.getString("note"),
                rs.getString("external_url"),
                rs.getString("file_name"),
                rs.getString("content_type"),
                sizeBytes,
                rs.getString("storage_path"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
