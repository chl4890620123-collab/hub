// TODO service keeps human confirmation, duplicate resolution, and execution transitions in one transaction boundary.
package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.TodoItem;
import com.hub.model.User;
import com.hub.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TodoService {
    private final TodoRepository todos;private final FeedbackRepository feedback;private final RevisionRepository revisions;
    private final TimelineRepository timeline;private final ProjectRepository projects;private final UserRepository users;private final EvidenceRepository evidence;private final ObjectMapper json;
    public TodoService(TodoRepository todos,FeedbackRepository feedback,RevisionRepository revisions,TimelineRepository timeline,
                       ProjectRepository projects,UserRepository users,EvidenceRepository evidence,ObjectMapper json){
        this.todos=todos;this.feedback=feedback;this.revisions=revisions;this.timeline=timeline;this.projects=projects;this.users=users;this.evidence=evidence;this.json=json;}
    public List<TodoItem> month(long projectId,int year,int month){LocalDate from=LocalDate.of(year,month,1);return todos.listMonth(projectId,from,from.plusMonths(1));}
    public List<TodoItem> undated(long projectId){return todos.listUndated(projectId);}
    public List<TodoItem> pending(long projectId){return todos.pending(projectId);}

    @Transactional
    public void confirm(TodoItem before,Long assigneeId,LocalDate dueDate,User actor){
        if(assigneeId==null)throw new IllegalArgumentException("업무 확정 전에 담당 MEMBER를 선택해 주세요.");
        if(!projects.isMember(before.projectId(),assigneeId))throw new IllegalArgumentException("담당자는 현재 프로젝트의 활성 MEMBER여야 합니다.");
        String confirmedAssignee=users.findById(assigneeId).filter(User::active).filter(u->!u.isAdmin()).map(User::displayName)
                .orElseThrow(()->new IllegalArgumentException("담당 MEMBER를 찾을 수 없습니다."));
        if(!todos.confirm(before.id(),actor.id(),assigneeId,confirmedAssignee,dueDate))
            throw new StateConflictException("이미 확정/제외된 TODO는 다시 확정할 수 없습니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"CONFIRM",json.writeValueAsString(before),"{\"confirmed\":true}");}
        catch(Exception e){throw new IllegalStateException(e);}
        if(before.dueDateSuggestion()!=null&&!before.dueDateSuggestion().equals(dueDate))feedback.add(before.projectId(),"TODO",before.id(),"due_date",before.dueDateSuggestion().toString(),String.valueOf(dueDate),"DUE_DATE_CORRECTION",actor.id());
        if(before.assigneeText()!=null&&!before.assigneeText().isBlank()&&!before.assigneeText().equals(confirmedAssignee))feedback.add(before.projectId(),"TODO",before.id(),"assignee",before.assigneeText(),confirmedAssignee,"ASSIGNEE_CORRECTION",actor.id());
        timeline.append(before.projectId(),"TODO_CONFIRMED",before.title(),null,LocalDateTime.now(),"TODO",before.id());
    }

    @Transactional
    public void reject(TodoItem before,User actor){
        if(!todos.reject(before.id(),actor.id()))throw new StateConflictException("이미 확정/제외된 TODO는 다시 제외할 수 없습니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"REJECT",json.writeValueAsString(before),"{\"rejected\":true}");}
        catch(Exception e){throw new IllegalStateException(e);}
    }

    /** Existing task stays authoritative; only the candidate's evidence is merged before the candidate is rejected. */
    @Transactional
    public void mergeDuplicate(TodoItem candidate,User actor){
        Long targetId=candidate.possibleDuplicateOfId();
        if(targetId==null)throw new IllegalArgumentException("연결된 중복 후보 업무가 없습니다.");
        TodoItem target=todos.find(targetId);
        if(target.projectId()!=candidate.projectId())throw new IllegalArgumentException("다른 프로젝트의 TODO에는 합칠 수 없습니다.");
        evidence.mergeTodoEvidence(candidate.id(),targetId);
        if(!todos.reject(candidate.id(),actor.id()))throw new StateConflictException("중복 후보 상태가 이미 변경되었습니다.");
        try{revisions.add(candidate.projectId(),"TODO",candidate.id(),actor.id(),"DUPLICATE_MERGE",json.writeValueAsString(candidate),
                "{\"mergedIntoTodoId\":"+targetId+",\"reviewStatus\":\"REJECTED\"}");}
        catch(Exception e){throw new IllegalStateException(e);}
        timeline.append(candidate.projectId(),"TODO_DUPLICATE_MERGED",candidate.title(),"기존 TODO #"+targetId+"에 근거 합침",LocalDateTime.now(),"TODO",targetId);
    }

    @Transactional
    public void updateStatus(TodoItem before,String status,User actor){
        if("REASSIGNMENT_REQUIRED".equals(before.assignmentStatus()))
            throw new StateConflictException("담당자 재배정이 필요한 TODO입니다. ADMIN이 먼저 새 담당자를 지정해 주세요.");
        if(status != null && status.equals(before.taskStatus())) return;
        if(!todos.updateTaskStatus(before.id(),status))throw new StateConflictException("확정되고 정상 배정된 TODO만 상태를 변경할 수 있습니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"STATUS_CHANGE",json.writeValueAsString(before),"{\"taskStatus\":\""+status+"\"}");}
        catch(Exception e){throw new IllegalStateException(e);}
        timeline.append(before.projectId(),"TODO_STATUS",before.title(),status,LocalDateTime.now(),"TODO",before.id());
    }
}
