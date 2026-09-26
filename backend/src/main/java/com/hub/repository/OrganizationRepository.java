package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class OrganizationRepository {
    private final JdbcTemplate jdbc;

    public OrganizationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Department(long id, String name, boolean active) {}
    public record Team(long id, long departmentId, String name, boolean active) {}
    public record Selection(long departmentId, String departmentName, long teamId, String teamName) {}

    public List<Department> departments(boolean activeOnly) {
        String where = activeOnly ? " WHERE active=TRUE" : "";
        return jdbc.query("SELECT id,name,active FROM organization_department" + where + " ORDER BY name,id",
                (rs,n) -> new Department(rs.getLong("id"), rs.getString("name"), rs.getBoolean("active")));
    }

    public List<Team> teams(boolean activeOnly) {
        String where = activeOnly ? " WHERE active=TRUE" : "";
        return jdbc.query("SELECT id,department_id,name,active FROM organization_team" + where + " ORDER BY department_id,name,id",
                (rs,n) -> new Team(rs.getLong("id"), rs.getLong("department_id"), rs.getString("name"), rs.getBoolean("active")));
    }

    public List<Team> teamsForDepartment(long departmentId, boolean activeOnly) {
        String active = activeOnly ? " AND active=TRUE" : "";
        return jdbc.query("SELECT id,department_id,name,active FROM organization_team WHERE department_id=?" + active + " ORDER BY name,id",
                (rs,n) -> new Team(rs.getLong("id"), rs.getLong("department_id"), rs.getString("name"), rs.getBoolean("active")),
                departmentId);
    }

    public Optional<Selection> activeSelection(Long departmentId, Long teamId) {
        if (departmentId == null || teamId == null) return Optional.empty();
        return jdbc.query("""
                SELECT d.id department_id,d.name department_name,t.id team_id,t.name team_name
                FROM organization_department d
                JOIN organization_team t ON t.department_id=d.id
                WHERE d.id=? AND t.id=? AND d.active=TRUE AND t.active=TRUE
                """, (rs,n) -> new Selection(rs.getLong("department_id"), rs.getString("department_name"),
                        rs.getLong("team_id"), rs.getString("team_name")), departmentId, teamId).stream().findFirst();
    }

    public Optional<Selection> selection(Long departmentId, Long teamId) {
        if (departmentId == null || teamId == null) return Optional.empty();
        return jdbc.query("""
                SELECT d.id department_id,d.name department_name,t.id team_id,t.name team_name
                FROM organization_department d
                JOIN organization_team t ON t.department_id=d.id
                WHERE d.id=? AND t.id=?
                """, (rs,n) -> new Selection(rs.getLong("department_id"), rs.getString("department_name"),
                        rs.getLong("team_id"), rs.getString("team_name")), departmentId, teamId).stream().findFirst();
    }

    public long createDepartment(String name) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps=c.prepareStatement("INSERT INTO organization_department(name,active) VALUES(?,TRUE)", new String[]{"id"});
            ps.setString(1, clean(name)); return ps;
        }, key);
        if (key.getKey()==null) throw new IllegalStateException("부서 ID를 만들지 못했습니다.");
        return key.getKey().longValue();
    }

    public long createTeam(long departmentId, String name) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM organization_department WHERE id=? AND active=TRUE", Long.class, departmentId) == 0)
            throw new IllegalArgumentException("사용 중인 부서를 선택해 주세요.");
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps=c.prepareStatement("INSERT INTO organization_team(department_id,name,active) VALUES(?,?,TRUE)", new String[]{"id"});
            ps.setLong(1, departmentId); ps.setString(2, clean(name)); return ps;
        }, key);
        if (key.getKey()==null) throw new IllegalStateException("팀 ID를 만들지 못했습니다.");
        return key.getKey().longValue();
    }

    public void renameDepartment(long id, String name) {
        if (jdbc.update("UPDATE organization_department SET name=? WHERE id=?", clean(name), id) != 1)
            throw new IllegalArgumentException("부서를 찾을 수 없습니다.");
        jdbc.update("UPDATE app_user SET department_name=? WHERE department_id=?", clean(name), id);
    }

    public void renameTeam(long id, String name) {
        if (jdbc.update("UPDATE organization_team SET name=? WHERE id=?", clean(name), id) != 1)
            throw new IllegalArgumentException("팀을 찾을 수 없습니다.");
        jdbc.update("UPDATE app_user SET team_name=? WHERE team_id=?", clean(name), id);
    }

    public void setDepartmentActive(long id, boolean active) {
        if (jdbc.update("UPDATE organization_department SET active=? WHERE id=?", active, id) != 1)
            throw new IllegalArgumentException("부서를 찾을 수 없습니다.");
        if (!active) jdbc.update("UPDATE organization_team SET active=FALSE WHERE department_id=?", id);
    }

    public void setTeamActive(long id, boolean active) {
        if (active) {
            Long departmentId = jdbc.queryForObject("SELECT department_id FROM organization_team WHERE id=?", Long.class, id);
            Boolean departmentActive = jdbc.queryForObject("SELECT active FROM organization_department WHERE id=?", Boolean.class, departmentId);
            if (!Boolean.TRUE.equals(departmentActive)) throw new IllegalArgumentException("비활성 부서의 팀은 활성화할 수 없습니다.");
        }
        if (jdbc.update("UPDATE organization_team SET active=? WHERE id=?", active, id) != 1)
            throw new IllegalArgumentException("팀을 찾을 수 없습니다.");
    }

    public void assignUser(long userId, Selection selection) {
        jdbc.update("UPDATE app_user SET department_id=?,team_id=?,department_name=?,team_name=? WHERE id=?",
                selection.departmentId(), selection.teamId(), selection.departmentName(), selection.teamName(), userId);
    }

    private static String clean(String value) {
        String v=value==null?"":value.trim();
        if (v.isBlank()) throw new IllegalArgumentException("이름을 입력해 주세요.");
        if (v.length()>200) throw new IllegalArgumentException("이름은 200자 이하여야 합니다.");
        return v;
    }
}
