package com.hub.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class SearchRuleRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SearchRuleRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Row(long id, long projectId, String name, List<String> aliases, List<String> patterns,
                      String targetFile, String mode, int priority, boolean active) {}

    public List<Row> list(long projectId, boolean activeOnly) {
        String sql = "SELECT id,project_id,name,aliases_json,patterns_json,target_file,mode,priority,active " +
                "FROM project_search_rule WHERE project_id=?" + (activeOnly ? " AND active=TRUE" : "") +
                " ORDER BY priority DESC,id ASC";
        return jdbc.query(sql, (rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("project_id"), rs.getString("name"),
                readStrings(rs.getString("aliases_json")), readStrings(rs.getString("patterns_json")),
                rs.getString("target_file"), rs.getString("mode"), rs.getInt("priority"), rs.getBoolean("active")
        ), projectId);
    }

    public Optional<Row> find(long projectId, long id) {
        return jdbc.query(
                "SELECT id,project_id,name,aliases_json,patterns_json,target_file,mode,priority,active FROM project_search_rule WHERE project_id=? AND id=?",
                (rs, n) -> new Row(rs.getLong("id"), rs.getLong("project_id"), rs.getString("name"),
                        readStrings(rs.getString("aliases_json")), readStrings(rs.getString("patterns_json")),
                        rs.getString("target_file"), rs.getString("mode"), rs.getInt("priority"), rs.getBoolean("active")),
                projectId, id).stream().findFirst();
    }

    public long create(long projectId, String name, List<String> aliases, List<String> patterns,
                       String targetFile, String mode, int priority, boolean active, long actorId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO project_search_rule(project_id,name,aliases_json,patterns_json,target_file,mode,priority,active,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    new String[]{"id"});
            ps.setLong(1, projectId); ps.setString(2, name); ps.setString(3, writeStrings(aliases));
            ps.setString(4, writeStrings(patterns)); ps.setString(5, blankToNull(targetFile)); ps.setString(6, mode);
            ps.setInt(7, priority); ps.setBoolean(8, active); ps.setLong(9, actorId); return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Search rule id was not generated");
        return key.getKey().longValue();
    }

    public void update(long projectId, long id, String name, List<String> aliases, List<String> patterns,
                       String targetFile, String mode, int priority, boolean active) {
        int changed = jdbc.update(
                "UPDATE project_search_rule SET name=?,aliases_json=?,patterns_json=?,target_file=?,mode=?,priority=?,active=?,updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND id=?",
                name, writeStrings(aliases), writeStrings(patterns), blankToNull(targetFile), mode, priority, active, projectId, id);
        if (changed != 1) throw new IllegalArgumentException("Search rule not found");
    }

    public void delete(long projectId, long id) {
        if (jdbc.update("DELETE FROM project_search_rule WHERE project_id=? AND id=?", projectId, id) != 1) {
            throw new IllegalArgumentException("Search rule not found");
        }
    }

    private List<String> readStrings(String value) {
        try { return json.readValue(value == null ? "[]" : value, new TypeReference<List<String>>() {}); }
        catch (Exception e) { throw new IllegalStateException("Invalid search rule JSON", e); }
    }

    private String writeStrings(List<String> value) {
        try { return json.writeValueAsString(value == null ? List.of() : value); }
        catch (Exception e) { throw new IllegalStateException("Search rule JSON failed", e); }
    }

    private static String blankToNull(String value) {
        String v = value == null ? "" : value.trim();
        return v.isBlank() ? null : v;
    }
}
