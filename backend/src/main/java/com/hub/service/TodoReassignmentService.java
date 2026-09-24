// ADMIN resolves explicit handoff work; assignments are never guessed during team/account changes.
package com.hub.service;

import com.hub.model.TodoItem;
import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.ReassignmentRepository;
import com.hub.repository.TodoRepository;
import com.hub.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class TodoReassignmentService {
    private final ReassignmentRepository reassignments;
    private final TodoRepository todos;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AuditRepository audit;
    private final GoogleCalendarService calendar;

    public TodoReassignmentService(ReassignmentRepository reassignments,TodoRepository todos,ProjectRepository projects,
                                   UserRepository users,AuditRepository audit,GoogleCalendarService calendar){
        this.reassignments=reassignments;this.todos=todos;this.projects=projects;this.users=users;this.audit=audit;this.calendar=calendar;
    }

    public List<Map<String,Object>> pending(long projectId){return reassignments.listPending(projectId);}

    /** Best-effort bulk resolve: each request is handled independently so one failure does not block the rest. */
    @Transactional
    public Map<Long,String> bulkResolve(long projectId,List<Long> requestIds,long newAssigneeId,User admin){
        java.util.Map<Long,String> results=new java.util.LinkedHashMap<>();
        for(Long requestId:requestIds){
            try{
                Map<String,Object> request=reassignments.findPending(requestId);
                if(((Number)request.get("project_id")).longValue()!=projectId)throw new IllegalArgumentException("다른 프로젝트의 요청입니다.");
                resolve(requestId,newAssigneeId,admin);
                results.put(requestId,"RESOLVED");
            }catch(Exception e){
                results.put(requestId,"FAILED: "+e.getMessage());
            }
        }
        return results;
    }

    @Transactional
    public void resolve(long requestId,long newAssigneeId,User admin){
        Map<String,Object> request=reassignments.findPending(requestId);
        long projectId=((Number)request.get("project_id")).longValue();
        long todoId=((Number)request.get("todo_id")).longValue();
        if(!projects.isMember(projectId,newAssigneeId))throw new IllegalArgumentException("새 담당자는 현재 프로젝트에 참여 중인 팀원이어야 합니다.");
        String name=users.findById(newAssigneeId).filter(User::active).filter(u->!u.isAdmin()).map(User::displayName)
                .orElseThrow(()->new IllegalArgumentException("새 담당 팀원을 찾을 수 없습니다."));
        TodoItem before=todos.find(todoId);
        if(!todos.reassign(todoId,newAssigneeId,name))throw new StateConflictException("할 일의 재배정 상태가 이미 변경되었습니다.");
        if(!reassignments.resolve(requestId,admin.id(),newAssigneeId))throw new StateConflictException("재배정 요청이 이미 처리되었습니다.");
        // The old assignee's calendar hold belongs to a person who no longer owns this TODO - drop
        // it and open a fresh one for the new assignee, the same way TodoService.confirm does for a
        // first-time assignment. Without this the old hold lingers forever (nothing else ever visits
        // it again) and the new assignee never gets one at all.
        if(before.googleCalendarEventId()!=null&&before.assigneeId()!=null)calendar.deleteEvent(before.assigneeId(),before.googleCalendarEventId());
        String newEventId=before.dueDate()!=null?calendar.createEvent(newAssigneeId,before.title(),before.description(),before.dueDate()):null;
        todos.setCalendarEventId(todoId,newEventId);
        audit.add(admin.id(),projectId,"TODO_REASSIGN","TODO",todoId,"{\"newAssigneeId\":"+newAssigneeId+"}");
    }
}
