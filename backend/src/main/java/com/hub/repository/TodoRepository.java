// TODO persistence centralizes candidate deduplication, assignment health, and retry-safe state transitions.
package com.hub.repository;

import com.hub.model.TodoItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Repository
public class TodoRepository {
    private final JdbcTemplate jdbc;
    public TodoRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    /** AI creates a review candidate only. Exact normalized duplicates are flagged, never auto-deleted. */
    public long create(long projectId,Long versionId,Long meetingId,String title,String description,
                       String assigneeText,Long ignoredSuggestionId,String assigneeSuggestionText,
                       LocalDate ignoredDueDate,LocalDate dueSuggestion,String confidence,
                       Long duplicateOf,String duplicateReason){
        String normalizedTitle=normalizeTitle(title);
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{
            PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO todo(project_id,source_document_version_id,source_meeting_id,title,description,
                    assignee_id,assignee_text,assignee_suggestion_id,assignee_suggestion_text,due_date,due_date_suggestion,confidence,
                    normalized_title,possible_duplicate_of_id,duplicate_reason)
                VALUES(?,?,?,?,?,NULL,?,NULL,?,NULL,?,?,?,?,?)
                """, new String[]{"id"});
            ps.setLong(1,projectId);
            if(versionId==null)ps.setNull(2,java.sql.Types.BIGINT);else ps.setLong(2,versionId);
            if(meetingId==null)ps.setNull(3,java.sql.Types.BIGINT);else ps.setLong(3,meetingId);
            ps.setString(4,title);ps.setString(5,description);ps.setString(6,assigneeText);ps.setString(7,assigneeSuggestionText);
            if(dueSuggestion==null)ps.setNull(8,java.sql.Types.DATE);else ps.setDate(8,Date.valueOf(dueSuggestion));
            ps.setString(9,confidence);ps.setString(10,normalizedTitle);
            if(duplicateOf==null)ps.setNull(11,java.sql.Types.BIGINT);else ps.setLong(11,duplicateOf);
            ps.setString(12,duplicateReason);return ps;
        },key);
        if(key.getKey()==null)throw new IllegalStateException("TODO id was not generated");
        return key.getKey().longValue();
    }

    public TodoItem find(long todoId){return jdbc.queryForObject(selectColumns()+" FROM todo WHERE id=?",(rs,n)->map(rs),todoId);}

    public List<TodoItem> listMonth(long projectId,LocalDate from,LocalDate to){
        return jdbc.query(selectColumns()+"""
             FROM todo WHERE project_id=? AND review_status='CONFIRMED'
               AND due_date>=? AND due_date<?
             ORDER BY due_date,id
            """,(rs,n)->map(rs),projectId,Date.valueOf(from),Date.valueOf(to));
    }

    public List<TodoItem> listUndated(long projectId){
        return jdbc.query(selectColumns()+" FROM todo WHERE project_id=? AND review_status='CONFIRMED' AND due_date IS NULL ORDER BY id DESC",
                (rs,n)->map(rs),projectId);
    }

    public List<TodoItem> listRecentConfirmed(long projectId,int limit){
        return jdbc.query(selectColumns()+" FROM todo WHERE project_id=? AND review_status='CONFIRMED' ORDER BY id DESC LIMIT ?",
                (rs,n)->map(rs),projectId,Math.max(1,Math.min(limit,500)));
    }

    public List<TodoItem> pending(long projectId){
        return jdbc.query(selectColumns()+" FROM todo WHERE project_id=? AND review_status IN ('AI_GENERATED','REVIEWING') ORDER BY id DESC",
                (rs,n)->map(rs),projectId);
    }

    public List<TodoItem> unfinishedAssigned(long projectId,long userId){
        return jdbc.query(selectColumns()+"""
                FROM todo WHERE project_id=? AND assignee_id=? AND review_status='CONFIRMED'
                  AND task_status<>'DONE' AND assignment_status='ACTIVE' ORDER BY id
                """,(rs,n)->map(rs),projectId,userId);
    }

    public boolean confirm(long todoId,long actorId,long assigneeId,String assigneeText,LocalDate dueDate){
        int updated=jdbc.update("""
            UPDATE todo SET assignee_id=?,assignee_text=?,due_date=?,review_status='CONFIRMED',assignment_status='ACTIVE',
              confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND review_status IN ('AI_GENERATED','REVIEWING')
            """,assigneeId,assigneeText,dueDate==null?null:Date.valueOf(dueDate),actorId,todoId);
        return updated==1;
    }

    public boolean reject(long todoId,long actorId){
        return jdbc.update("""
            UPDATE todo SET review_status='REJECTED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND review_status IN ('AI_GENERATED','REVIEWING')
            """,actorId,todoId)==1;
    }

    public boolean updateTaskStatus(long todoId,String status){
        return jdbc.update("""
                UPDATE todo SET task_status=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND review_status='CONFIRMED' AND assignment_status='ACTIVE'
                """,status,todoId)==1;
    }

    /** Returns true only for the transaction that moved an ACTIVE assignment into the handoff state. */
    public boolean requireReassignment(long todoId){
        return jdbc.update("UPDATE todo SET assignment_status='REASSIGNMENT_REQUIRED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND assignment_status='ACTIVE'",todoId)==1;
    }

    public boolean reassign(long todoId,long newAssigneeId,String assigneeText){
        return jdbc.update("""
                UPDATE todo SET assignee_id=?,assignee_text=?,assignment_status='ACTIVE',updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND review_status='CONFIRMED' AND assignment_status='REASSIGNMENT_REQUIRED'
                """,newAssigneeId,assigneeText,todoId)==1;
    }


    public List<TitleRow> recentOpenTitles(long projectId,int limit){
        return jdbc.query("""
                SELECT id,title,review_status FROM todo WHERE project_id=? AND review_status<>'REJECTED'
                ORDER BY id DESC LIMIT ?
                """,(rs,n)->new TitleRow(rs.getLong("id"),rs.getString("title"),rs.getString("review_status")),projectId,Math.max(1,Math.min(limit,500)));
    }

    public record TitleRow(long id,String title,String reviewStatus){}

    public static String normalizeTitle(String title){
        if(title==null)return "";
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","").trim();
    }

    private static String selectColumns(){return """
        SELECT id,project_id,title,description,assignee_id,assignee_text,assignee_suggestion_id,
               assignee_suggestion_text,due_date,due_date_suggestion,confidence,review_status,task_status,
               assignment_status,possible_duplicate_of_id,duplicate_reason,created_at
        """;}
    private TodoItem map(java.sql.ResultSet rs)throws java.sql.SQLException{
        Date due=rs.getDate("due_date"),suggestion=rs.getDate("due_date_suggestion");
        long a=rs.getLong("assignee_id");Long aid=rs.wasNull()?null:a;
        long c=rs.getLong("assignee_suggestion_id");Long cid=rs.wasNull()?null:c;
        long d=rs.getLong("possible_duplicate_of_id");Long duplicateId=rs.wasNull()?null:d;
        return new TodoItem(rs.getLong("id"),rs.getLong("project_id"),rs.getString("title"),rs.getString("description"),aid,
                rs.getString("assignee_text"),cid,rs.getString("assignee_suggestion_text"),due==null?null:due.toLocalDate(),
                suggestion==null?null:suggestion.toLocalDate(),rs.getString("confidence"),rs.getString("review_status"),
                rs.getString("task_status"),rs.getString("assignment_status"),duplicateId,rs.getString("duplicate_reason"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
