package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RevisionRepository {
    private final JdbcTemplate jdbc;
    public RevisionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void add(long projectId, String entityType, long entityId, long actorId, String action, String beforeJson, String afterJson) {
        jdbc.update("INSERT INTO revision_history(project_id,entity_type,entity_id,actor_id,action,before_json,after_json) VALUES(?,?,?,?,?,?,?)",
                projectId,entityType,entityId,actorId,action,beforeJson,afterJson);
    }

    public List<Map<String,Object>> list(long projectId, int limit) {
        return jdbc.queryForList("""
                SELECT r.id,r.entity_type,r.entity_id,r.action,r.before_json,r.after_json,r.created_at,
                       u.display_name actor_name
                FROM revision_history r
                JOIN app_user u ON u.id=r.actor_id
                WHERE r.project_id=?
                ORDER BY r.id DESC LIMIT ?
                """, projectId, Math.max(1, Math.min(limit, 500)));
    }
}
