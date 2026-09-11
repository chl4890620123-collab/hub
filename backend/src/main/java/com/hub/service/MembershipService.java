// single transaction boundary for project join/leave/move and unfinished-work handoff.
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

@Service
public class MembershipService {
    private final ProjectRepository projects;
    private final TodoRepository todos;
    private final ReassignmentRepository reassignments;
    private final UserRepository users;
    private final AuditRepository audit;

    public MembershipService(ProjectRepository projects,TodoRepository todos,ReassignmentRepository reassignments,
                             UserRepository users,AuditRepository audit){
        this.projects=projects;this.todos=todos;this.reassignments=reassignments;this.users=users;this.audit=audit;
    }

    @Transactional
    public void add(long projectId,long userId,User actor,String reason){
        User target=users.findById(userId).filter(User::active).filter(u->!u.isAdmin())
                .orElseThrow(()->new IllegalArgumentException("활성 MEMBER만 프로젝트에 추가할 수 있습니다."));
        if(projects.addMember(projectId,userId)){
            projects.recordMemberJoin(projectId,userId,actor==null?null:actor.id(),reason);
            audit.add(actor==null?null:actor.id(),projectId,"PROJECT_MEMBER_JOIN","USER",userId,"{\"reason\":\""+safe(reason)+"\"}");
        }
    }

    @Transactional
    public int remove(long projectId,long userId,User actor,String reason){
        if(!projects.isMember(projectId,userId))return 0;
        int queued=queueUnfinished(projectId,userId,actor==null?null:actor.id(),reason);
        if(projects.removeMember(projectId,userId)){
            projects.recordMemberLeave(projectId,userId,actor==null?null:actor.id(),reason);
            audit.add(actor==null?null:actor.id(),projectId,"PROJECT_MEMBER_LEAVE","USER",userId,
                    "{\"reason\":\""+safe(reason)+"\",\"reassignmentCount\":"+queued+"}");
        }
        return queued;
    }

    @Transactional
    public int move(long fromProjectId,long toProjectId,long userId,User actor){
        if(fromProjectId==toProjectId)throw new IllegalArgumentException("기존 프로젝트와 이동할 프로젝트가 같습니다.");
        int queued=remove(fromProjectId,userId,actor,"PROJECT_MOVE");
        add(toProjectId,userId,actor,"PROJECT_MOVE");
        return queued;
    }

    @Transactional
    public int removeFromAllProjects(long userId,User actor,String reason){
        int total=0;
        for(Long projectId:projects.listProjectIdsForUser(userId)) total+=remove(projectId,userId,actor,reason);
        return total;
    }

    private int queueUnfinished(long projectId,long userId,Long actorId,String reason){
        List<TodoItem> open=todos.unfinishedAssigned(projectId,userId);
        int queued=0;
        for(TodoItem todo:open){
            // Conditional update makes concurrent remove/retry requests idempotent without a second "pending" business record.
            if(todos.requireReassignment(todo.id())){
                reassignments.enqueue(todo.id(),projectId,userId,reason,actorId);
                queued++;
            }
        }
        return queued;
    }

    private static String safe(String raw){return raw==null?"":raw.replace("\\","\\\\").replace("\"","\\\"");}
}
