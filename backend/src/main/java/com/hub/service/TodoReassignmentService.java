// ADMIN resolves explicit handoff work; assignments are never guessed during team/account changes.
package com.hub.service;

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

    public TodoReassignmentService(ReassignmentRepository reassignments,TodoRepository todos,ProjectRepository projects,
                                   UserRepository users,AuditRepository audit){
        this.reassignments=reassignments;this.todos=todos;this.projects=projects;this.users=users;this.audit=audit;
    }

    public List<Map<String,Object>> pending(long projectId){return reassignments.listPending(projectId);}

    @Transactional
    public void resolve(long requestId,long newAssigneeId,User admin){
        Map<String,Object> request=reassignments.findPending(requestId);
        long projectId=((Number)request.get("project_id")).longValue();
        long todoId=((Number)request.get("todo_id")).longValue();
        if(!projects.isMember(projectId,newAssigneeId))throw new IllegalArgumentException("새 담당자는 현재 프로젝트의 활성 MEMBER여야 합니다.");
        String name=users.findById(newAssigneeId).filter(User::active).filter(u->!u.isAdmin()).map(User::displayName)
                .orElseThrow(()->new IllegalArgumentException("새 담당 MEMBER를 찾을 수 없습니다."));
        if(!todos.reassign(todoId,newAssigneeId,name))throw new StateConflictException("TODO 재배정 상태가 이미 변경되었습니다.");
        if(!reassignments.resolve(requestId,admin.id(),newAssigneeId))throw new StateConflictException("재배정 요청이 이미 처리되었습니다.");
        audit.add(admin.id(),projectId,"TODO_REASSIGN","TODO",todoId,"{\"newAssigneeId\":"+newAssigneeId+"}");
    }
}
