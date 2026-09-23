// current project membership stays small and fast; a separate history table preserves every join/leave event.
package com.hub.repository;

import com.hub.model.Project;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class ProjectRepository {
    private final JdbcTemplate jdbc;
    public ProjectRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ProjectOption(long id, String name) {}

    /** Names only, for the signup screen's project picker - shown before the visitor has any access. */
    public List<ProjectOption> listAll() {
        return jdbc.query("SELECT id,name FROM project ORDER BY name,id",
                (rs, n) -> new ProjectOption(rs.getLong("id"), rs.getString("name")));
    }

    public void rename(long projectId, String name, String description) {
        jdbc.update("UPDATE project SET name=?,description=? WHERE id=?", name, description, projectId);
    }

    public boolean exists(long projectId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=?", Long.class, projectId);
        return count != null && count > 0;
    }

    public List<Project> listForUser(long userId, boolean admin) {
        if (admin) {
            return jdbc.query("SELECT p.id,p.name,p.description,p.created_by FROM project p ORDER BY p.id",
                    (rs,n)->new Project(rs.getLong("id"),rs.getString("name"),rs.getString("description"),rs.getLong("created_by"),"ADMIN",true));
        }
        return jdbc.query("""
                SELECT p.id,p.name,p.description,p.created_by,pm.can_confirm_todos
                FROM project p JOIN project_member pm ON pm.project_id=p.id WHERE pm.user_id=? ORDER BY p.id
                """,
                (rs,n)->new Project(rs.getLong("id"),rs.getString("name"),rs.getString("description"),rs.getLong("created_by"),
                        "MEMBER",rs.getBoolean("can_confirm_todos")), userId);
    }

    public long create(String name, String description, long userId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO project(name,description,created_by) VALUES(?,?,?)", new String[]{"id"});
            ps.setString(1, name); ps.setString(2, description); ps.setLong(3, userId); return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Project id was not generated");
        return key.getKey().longValue();
    }

    /** Returns true only when a new current membership row was created. */
    public boolean addMember(long projectId, long userId) {
        try {
            return jdbc.update("INSERT INTO project_member(project_id,user_id) VALUES(?,?)", projectId, userId) == 1;
        } catch (DuplicateKeyException ignored) {
            return false; // Retry/double-click is idempotent.
        }
    }

    public void recordMemberJoin(long projectId,long userId,Long actorId,String reason) {
        jdbc.update("INSERT INTO project_member_history(project_id,user_id,join_reason,actor_id) VALUES(?,?,?,?)",
                projectId,userId,reason,actorId);
    }

    public void recordMemberLeave(long projectId,long userId,Long actorId,String reason) {
        jdbc.update("""
                UPDATE project_member_history SET left_at=CURRENT_TIMESTAMP,leave_reason=?,actor_id=COALESCE(?,actor_id)
                WHERE id=(SELECT MAX(id) FROM project_member_history WHERE project_id=? AND user_id=? AND left_at IS NULL)
                """,reason,actorId,projectId,userId);
    }

    public List<java.util.Map<String,Object>> listMembers(long projectId) {
        return jdbc.queryForList("""
                SELECT u.id user_id,u.display_name,u.login_id,u.email,'MEMBER' project_role,u.account_status,pm.can_confirm_todos
                FROM project_member pm JOIN app_user u ON u.id=pm.user_id
                WHERE pm.project_id=? AND u.account_status='ACTIVE' AND u.global_role='MEMBER' ORDER BY u.display_name,u.id
                """, projectId);
    }

    /** Global ADMIN always passes independently of this - see ProjectAccessService.requireConfirmPermission. */
    public boolean canConfirm(long projectId, long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_member WHERE project_id=? AND user_id=? AND can_confirm_todos=TRUE",
                Integer.class, projectId, userId);
        return count != null && count > 0;
    }

    /** Returns true only when the membership row exists (a non-member cannot be granted confirm permission). */
    public boolean setConfirmPermission(long projectId, long userId, boolean granted) {
        return jdbc.update("UPDATE project_member SET can_confirm_todos=? WHERE project_id=? AND user_id=?",
                granted, projectId, userId) == 1;
    }

    public boolean removeMember(long projectId, long userId) {
        return jdbc.update("DELETE FROM project_member WHERE project_id=? AND user_id=?", projectId, userId) == 1;
    }

    public List<Long> listProjectIdsForUser(long userId) {
        return jdbc.query("SELECT project_id FROM project_member WHERE user_id=? ORDER BY project_id", (rs,n)->rs.getLong(1), userId);
    }


    public boolean isMember(long projectId, long userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_member pm JOIN app_user u ON u.id=pm.user_id
                WHERE pm.project_id=? AND pm.user_id=? AND u.account_status='ACTIVE' AND u.global_role='MEMBER'
                """, Integer.class, projectId, userId);
        return count != null && count > 0;
    }
    public boolean canAccess(long projectId, long userId, boolean admin) { return admin || isMember(projectId, userId); }
}
