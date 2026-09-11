package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AiRunRepository {
    private final JdbcTemplate jdbc;

    public AiRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> successfulDocument(long versionId, String kind) {
        return firstJson(
                """
                SELECT raw_json FROM ai_run
                WHERE document_version_id=? AND kind=? AND status='SUCCESS'
                ORDER BY id DESC LIMIT 1
                """,
                versionId,
                kind
        );
    }

    public Optional<String> successfulMeeting(long meetingId, String kind) {
        return firstJson(
                """
                SELECT raw_json FROM ai_run
                WHERE meeting_id=? AND kind=? AND status='SUCCESS'
                ORDER BY id DESC LIMIT 1
                """,
                meetingId,
                kind
        );
    }

    public void saveDocument(long projectId, long versionId, String kind, String rawJson) {
        jdbc.update(
                """
                INSERT INTO ai_run(project_id,document_version_id,kind,status,provider,raw_json)
                VALUES(?,?,?,'SUCCESS','AI_SERVICE',?)
                """,
                projectId, versionId, kind, rawJson
        );
    }

    public void saveMeeting(long projectId, long meetingId, String kind, String rawJson) {
        jdbc.update(
                """
                INSERT INTO ai_run(project_id,meeting_id,kind,status,provider,raw_json)
                VALUES(?,?,?,'SUCCESS','AI_SERVICE',?)
                """,
                projectId, meetingId, kind, rawJson
        );
    }

    private Optional<String> firstJson(String sql, Object... args) {
        List<String> rows = jdbc.query(sql, (rs, n) -> rs.getString(1), args);
        return rows.stream().filter(value -> value != null && !value.isBlank()).findFirst();
    }
}
