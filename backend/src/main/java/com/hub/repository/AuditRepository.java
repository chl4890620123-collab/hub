package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbc;
    public AuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void add(Long userId, Long projectId, String action, String targetType, Long targetId, String detailJson) {
        jdbc.update("INSERT INTO audit_log(user_id,project_id,action,target_type,target_id,detail_json) VALUES(?,?,?,?,?,?)",
                userId, projectId, action, targetType, targetId, detailJson);
    }

    public List<Map<String,Object>> list(int limit) {
        return jdbc.queryForList("""
                SELECT a.id,a.user_id,u.display_name,a.project_id,p.name project_name,a.action,
                       a.target_type,a.target_id,a.detail_json,a.created_at
                FROM audit_log a
                LEFT JOIN app_user u ON u.id=a.user_id
                LEFT JOIN project p ON p.id=a.project_id
                ORDER BY a.id DESC LIMIT ?
                """, Math.max(1, Math.min(limit, 500)));
    }
}
