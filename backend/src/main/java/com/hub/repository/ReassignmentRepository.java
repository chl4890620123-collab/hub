// unfinished work is never silently reassigned when membership/account state changes.
package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ReassignmentRepository {
    private final JdbcTemplate jdbc;
    public ReassignmentRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public boolean pendingExists(long todoId){
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM todo_reassignment WHERE todo_id=? AND status='PENDING'",Integer.class,todoId);
        return count!=null&&count>0;
    }

    public void enqueue(long todoId,long projectId,Long formerAssigneeId,String reason,Long actorId){
        if(pendingExists(todoId))return;
        jdbc.update("""
                INSERT INTO todo_reassignment(todo_id,project_id,former_assignee_id,reason,created_by)
                VALUES(?,?,?,?,?)
                """,todoId,projectId,formerAssigneeId,reason,actorId);
    }

    public List<Map<String,Object>> listPending(long projectId){
        return jdbc.queryForList("""
                SELECT r.id,r.todo_id,r.project_id,r.former_assignee_id,u.display_name former_assignee_name,
                       r.reason,r.created_at,t.title,t.task_status,t.due_date
                FROM todo_reassignment r
                JOIN todo t ON t.id=r.todo_id
                LEFT JOIN app_user u ON u.id=r.former_assignee_id
                WHERE r.project_id=? AND r.status='PENDING'
                ORDER BY r.created_at,r.id
                """,projectId);
    }

    public Map<String,Object> findPending(long id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM todo_reassignment WHERE id=? AND status='PENDING'",id);
        if(rows.isEmpty())throw new IllegalArgumentException("재배정 요청을 찾을 수 없습니다.");
        return rows.get(0);
    }

    public boolean resolve(long id,long actorId,long newAssigneeId){
        return jdbc.update("""
                UPDATE todo_reassignment SET status='RESOLVED',resolved_by=?,resolved_at=CURRENT_TIMESTAMP,new_assignee_id=?
                WHERE id=? AND status='PENDING'
                """,actorId,newAssigneeId,id)==1;
    }
}
