// decision candidates use one-way review transitions so repeated confirmations cannot duplicate history.
package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@Repository
public class DecisionRepository {
    private final JdbcTemplate jdbc;
    public DecisionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long create(long projectId, Long versionId, Long meetingId, String statement, String confidence) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO decision_candidate(project_id,source_document_version_id,source_meeting_id,statement,confidence) VALUES(?,?,?,?,?)", new String[]{"id"});
            ps.setLong(1, projectId);
            if (versionId == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, versionId);
            if (meetingId == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, meetingId);
            ps.setString(4, statement); ps.setString(5, confidence);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Decision candidate id was not generated");
        return key.getKey().longValue();
    }

    public void linkEvidence(long decisionId, long evidenceId) {
        jdbc.update("INSERT INTO decision_evidence(decision_id,evidence_id) VALUES(?,?)", decisionId, evidenceId);
    }

    public List<Map<String,Object>> list(long projectId, int limit) {
        return jdbc.queryForList(
                "SELECT id,statement,confidence,review_status,created_at FROM decision_candidate WHERE project_id=? ORDER BY id DESC LIMIT ?",
                projectId, Math.max(1, Math.min(limit, 500))
        );
    }

    /** Work Context consumes confirmed organizational decisions only; candidates remain in ADMIN review. */
    public List<Map<String,Object>> listConfirmed(long projectId, int limit) {
        return jdbc.queryForList(
                "SELECT id,statement,confidence,review_status,created_at FROM decision_candidate WHERE project_id=? AND review_status='CONFIRMED' ORDER BY id DESC LIMIT ?",
                projectId, Math.max(1, Math.min(limit, 500))
        );
    }

    public List<Map<String,Object>> pending(long projectId) {
        return jdbc.queryForList("SELECT id,statement,confidence,review_status,created_at FROM decision_candidate WHERE project_id=? AND review_status IN ('AI_GENERATED','REVIEWING') ORDER BY id DESC", projectId);
    }

    public boolean confirm(long id, long actorId) {
        return jdbc.update("UPDATE decision_candidate SET review_status='CONFIRMED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP WHERE id=? AND review_status IN ('AI_GENERATED','REVIEWING')", actorId, id) == 1;
    }

    public boolean reject(long id, long actorId) {
        return jdbc.update("UPDATE decision_candidate SET review_status='REJECTED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP WHERE id=? AND review_status IN ('AI_GENERATED','REVIEWING')", actorId, id) == 1;
    }

    public long projectId(long id) { return jdbc.queryForObject("SELECT project_id FROM decision_candidate WHERE id=?", Long.class, id); }
}
