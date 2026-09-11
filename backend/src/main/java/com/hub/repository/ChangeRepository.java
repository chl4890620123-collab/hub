// change candidates are idempotent; repeated confirmation cannot duplicate review history.
package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@Repository
public class ChangeRepository {
    private final JdbcTemplate jdbc;

    public ChangeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long createAnalysis(long projectId, long beforeVersion, long afterVersion, long actorId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO change_analysis(project_id,before_version_id,after_version_id,created_by) VALUES(?,?,?,?)",
                    new String[]{"id"}
            );
            ps.setLong(1, projectId);
            ps.setLong(2, beforeVersion);
            ps.setLong(3, afterVersion);
            ps.setLong(4, actorId);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Change analysis id was not generated");
        return key.getKey().longValue();
    }

    public long addItem(long analysisId, String category, String before, String after, String reason) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO change_item(analysis_id,category,before_text,after_text,reason) VALUES(?,?,?,?,?)",
                    new String[]{"id"}
            );
            ps.setLong(1, analysisId);
            ps.setString(2, category);
            ps.setString(3, before);
            ps.setString(4, after);
            ps.setString(5, reason);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Change item id was not generated");
        return key.getKey().longValue();
    }

    public void linkEvidence(long itemId, long evidenceId, String side) {
        jdbc.update(
                "INSERT INTO change_evidence(change_item_id,evidence_id,evidence_side) VALUES(?,?,?)",
                itemId, evidenceId, side
        );
    }

    public long projectIdForItem(long itemId) {
        return jdbc.queryForObject(
                "SELECT ca.project_id FROM change_item ci JOIN change_analysis ca ON ca.id=ci.analysis_id WHERE ci.id=?",
                Long.class,
                itemId
        );
    }

    public boolean confirmItem(long itemId) {
        return jdbc.update("UPDATE change_item SET review_status='CONFIRMED' WHERE id=? AND review_status IN ('AI_GENERATED','REVIEWING')", itemId) == 1;
    }

    public List<Map<String, Object>> pending(long projectId) {
        return jdbc.queryForList(
                """
                SELECT ci.id,ci.category,ci.before_text,ci.after_text,ci.reason,ci.review_status,ca.created_at
                FROM change_item ci JOIN change_analysis ca ON ca.id=ci.analysis_id
                WHERE ca.project_id=? AND ci.review_status IN ('AI_GENERATED','REVIEWING')
                ORDER BY ci.id DESC
                """,
                projectId
        );
    }

    /** Work Context consumes confirmed changes only; AI-generated change candidates stay review-only. */
    public List<Map<String, Object>> listConfirmed(long projectId) {
        return jdbc.queryForList(
                """
                SELECT ci.id,ci.category,ci.before_text,ci.after_text,ci.reason,ci.review_status,ca.created_at
                FROM change_item ci JOIN change_analysis ca ON ca.id=ci.analysis_id
                WHERE ca.project_id=? AND ci.review_status='CONFIRMED'
                ORDER BY ci.id DESC
                """,
                projectId
        );
    }

    public List<Map<String, Object>> list(long projectId) {
        return jdbc.queryForList(
                """
                SELECT ci.id,ci.category,ci.before_text,ci.after_text,ci.reason,ci.review_status,ca.created_at
                FROM change_item ci JOIN change_analysis ca ON ca.id=ci.analysis_id
                WHERE ca.project_id=?
                ORDER BY ci.id DESC
                """,
                projectId
        );
    }
}
