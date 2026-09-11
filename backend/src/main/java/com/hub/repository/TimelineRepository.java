package com.hub.repository;

import com.hub.model.TimelineEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TimelineRepository {
    private final JdbcTemplate jdbc;
    public TimelineRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void append(long projectId, String type, String title, String description, LocalDateTime happenedAt,
                       String sourceType, Long sourceId) {
        jdbc.update("INSERT INTO timeline_event(project_id,event_type,title,description,happened_at,source_type,source_id) VALUES(?,?,?,?,?,?,?)",
                projectId,type,title,description,java.sql.Timestamp.valueOf(happenedAt),sourceType,sourceId);
    }

    public List<TimelineEvent> list(long projectId, int limit) {
        return jdbc.query("SELECT id,project_id,event_type,title,description,happened_at,source_type,source_id FROM timeline_event WHERE project_id=? ORDER BY happened_at DESC,id DESC LIMIT ?",
                (rs,n)->new TimelineEvent(rs.getLong("id"),rs.getLong("project_id"),rs.getString("event_type"),
                        rs.getString("title"),rs.getString("description"),rs.getTimestamp("happened_at").toLocalDateTime(),
                        rs.getString("source_type"),nullableLong(rs, "source_id")), projectId, limit);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
