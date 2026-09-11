package com.hub.repository;

import com.hub.model.ProjectMemory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProjectMemoryRepository {
    private final JdbcTemplate jdbc;
    public ProjectMemoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void upsert(long projectId, String key, String value, String type, String sourceType, Long sourceId, long actorId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM project_memory WHERE project_id=? AND memory_key=?", Integer.class, projectId, key);
        if (count != null && count > 0) {
            jdbc.update("UPDATE project_memory SET memory_value=?,memory_type=?,source_type=?,source_id=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND memory_key=?",
                    value,type,sourceType,sourceId,actorId,projectId,key);
        } else {
            jdbc.update("INSERT INTO project_memory(project_id,memory_key,memory_value,memory_type,source_type,source_id,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?)",
                    projectId,key,value,type,sourceType,sourceId,actorId,actorId);
        }
    }

    public List<ProjectMemory> list(long projectId) {
        return jdbc.query("SELECT id,project_id,memory_key,memory_value,memory_type,review_status,source_type,source_id,updated_at FROM project_memory WHERE project_id=? ORDER BY updated_at DESC",
                (rs,n)->new ProjectMemory(rs.getLong("id"),rs.getLong("project_id"),rs.getString("memory_key"),rs.getString("memory_value"),
                        rs.getString("memory_type"),rs.getString("review_status"),rs.getString("source_type"),nullableLong(rs, "source_id"),
                        rs.getTimestamp("updated_at").toLocalDateTime()), projectId);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
