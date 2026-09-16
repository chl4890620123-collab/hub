package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class ConnectorPolicyRepository {
    private final JdbcTemplate jdbc;
    public ConnectorPolicyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Boolean> list() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        jdbc.query("SELECT connector_type,enabled FROM connector_policy ORDER BY connector_type",
                rs -> { result.put(rs.getString("connector_type"), rs.getBoolean("enabled")); });
        return result;
    }

    /** An unknown type (not seeded) defaults open rather than silently blocking a connector nobody configured a policy row for. */
    public boolean isEnabled(String type) {
        java.util.List<Boolean> rows = jdbc.query("SELECT enabled FROM connector_policy WHERE connector_type=?",
                (rs, n) -> rs.getBoolean("enabled"), type);
        return rows.isEmpty() || rows.get(0);
    }

    public void setEnabled(String type, boolean enabled) {
        jdbc.update("UPDATE connector_policy SET enabled=? WHERE connector_type=?", enabled, type);
    }
}
