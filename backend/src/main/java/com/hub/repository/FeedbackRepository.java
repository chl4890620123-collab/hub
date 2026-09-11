package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {
    private final JdbcTemplate jdbc;
    public FeedbackRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void add(long projectId, String entityType, long entityId, String field, String aiValue,
                    String humanValue, String type, long actorId) {
        jdbc.update("INSERT INTO feedback(project_id,entity_type,entity_id,field_name,ai_value,human_value,feedback_type,created_by) VALUES(?,?,?,?,?,?,?,?)",
                projectId, entityType, entityId, field, aiValue, humanValue, type, actorId);
    }
}
