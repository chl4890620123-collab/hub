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
    private final GoogleCalendarService calendar;
    public TodoService(TodoRepository todos,FeedbackRepository feedback,RevisionRepository revisions,TimelineRepository timeline,
                       ProjectRepository projects,UserRepository users,EvidenceRepository evidence,ObjectMapper json,GoogleCalendarService calendar){
        this.todos=todos;this.feedback=feedback;this.revisions=revisions;this.timeline=timeline;this.projects=projects;this.users=users;this.evidence=evidence;this.json=json;this.calendar=calendar;}
    public List<TodoItem> month(long projectId,int year,int month){LocalDate from=LocalDate.of(year,month,1);return todos.listMonth(projectId,from,from.plusMonths(1));}
    public List<TodoItem> undated(long projectId){return todos.listUndated(projectId);}
    public List<TodoItem> dueThrough(long projectId,LocalDate through){return todos.listDueThrough(projectId,through);}
    public List<TodoItem> pending(long projectId){return todos.pending(projectId);}

    /** Registering a document with a due date can create its follow-up task in the same step. */
    @Transactional
    public long createManual(long projectId,Long versionId,String title,Long assigneeId,LocalDate dueDate,User actor){
        if(title==null||title.isBlank())throw new IllegalArgumentException("할 일 제목을 입력해 주세요.");
        if(assigneeId==null||dueDate==null)throw new IllegalArgumentException("후속 할 일에는 담당자와 기한이 모두 필요합니다.");
        String assigneeText=null;
        if(assigneeId!=null){
            if(!projects.isMember(projectId,assigneeId))throw new IllegalArgumentException("담당자는 현재 프로젝트에 참여 중인 팀원이어야 합니다.");
            assigneeText=users.findById(assigneeId).filter(User::active).filter(u->!u.isAdmin()).map(User::displayName)
                    .orElseThrow(()->new IllegalArgumentException("담당 팀원을 찾을 수 없습니다."));
        }
        long id=todos.createConfirmed(projectId,versionId,title.trim(),assigneeId,assigneeText,dueDate,actor.id());
        timeline.append(projectId,"TODO_CREATED",title.trim(),null,LocalDateTime.now(),"TODO",id);
        if(assigneeId!=null&&dueDate!=null){
            String eventId=calendar.createEvent(assigneeId,title.trim(),null,dueDate);
            if(eventId!=null)todos.setCalendarEventId(id,eventId);
        }
        return id;
    }

    @Transactional
    public void confirm(TodoItem before,Long assigneeId,LocalDate dueDate,User actor){
        if(assigneeId==null)throw new IllegalArgumentException("할 일을 확정하기 전에 담당 팀원을 선택해 주세요.");
        if(!projects.isMember(before.projectId(),assigneeId))throw new IllegalArgumentException("담당자는 현재 프로젝트에 참여 중인 팀원이어야 합니다.");
        String confirmedAssignee=users.findById(assigneeId).filter(User::active).filter(u->!u.isAdmin()).map(User::displayName)
                .orElseThrow(()->new IllegalArgumentException("담당 팀원을 찾을 수 없습니다."));
        if(!todos.confirm(before.id(),actor.id(),assigneeId,confirmedAssignee,dueDate))
            throw new StateConflictException("이미 확정했거나 제외한 할 일은 다시 확정할 수 없습니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"CONFIRM",json.writeValueAsString(before),"{\"confirmed\":true}");}
        catch(Exception e){throw new IllegalStateException(e);}
        if(before.dueDateSuggestion()!=null&&!before.dueDateSuggestion().equals(dueDate))feedback.add(before.projectId(),"TODO",before.id(),"due_date",before.dueDateSuggestion().toString(),String.valueOf(dueDate),"DUE_DATE_CORRECTION",actor.id());
        if(before.assigneeText()!=null&&!before.assigneeText().isBlank()&&!before.assigneeText().equals(confirmedAssignee))feedback.add(before.projectId(),"TODO",before.id(),"assignee",before.assigneeText(),confirmedAssignee,"ASSIGNEE_CORRECTION",actor.id());
        timeline.append(before.projectId(),"TODO_CONFIRMED",before.title(),null,LocalDateTime.now(),"TODO",before.id());
        if(dueDate!=null){
            String eventId=calendar.createEvent(assigneeId,before.title(),before.description(),dueDate);
            if(eventId!=null)todos.setCalendarEventId(before.id(),eventId);
        }
    }

    @Transactional
    public void editCandidate(TodoItem before,String title,String description,User actor){
        if(title==null||title.isBlank())throw new IllegalArgumentException("할 일 제목을 입력해 주세요.");
        if(!todos.editCandidate(before.id(),title.trim(),description))
            throw new StateConflictException("이미 확정했거나 제외한 할 일은 수정할 수 없습니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"CANDIDATE_EDIT",json.writeValueAsString(before),
                json.writeValueAsString(java.util.Map.of("title",title.trim())));}
        catch(Exception e){throw new IllegalStateException(e);}
    }

    /** Best-effort bulk assignment: each candidate is confirmed independently so one failure does not block the rest. */
    @Transactional
    public java.util.Map<Long,String> bulkConfirm(long projectId,List<Long> todoIds,Long assigneeId,LocalDate dueDate,User actor){
        java.util.Map<Long,String> results=new java.util.LinkedHashMap<>();
        for(Long id:todoIds){
            try{
                TodoItem before=todos.find(id);
                if(before.projectId()!=projectId)throw new IllegalArgumentException("다른 프로젝트의 할 일입니다.");
                confirm(before,assigneeId,dueDate,actor);
                results.put(id,"CONFIRMED");
            }catch(Exception e){
                results.put(id,"FAILED: "+e.getMessage());
            }
        }
        return results;
    }

    @Transactional
    public void reject(TodoItem before,User actor){
        if(!todos.reject(before.id(),actor.id()))throw new StateConflictException("이미 확정했거나 제외한 할 일은 다시 제외할 수 없습니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"REJECT",json.writeValueAsString(before),"{\"rejected\":true}");}
        catch(Exception e){throw new IllegalStateException(e);}
    }

    /** Existing task stays authoritative; only the candidate's evidence is merged before the candidate is rejected. */
    @Transactional
    public void mergeDuplicate(TodoItem candidate,User actor){
        Long targetId=candidate.possibleDuplicateOfId();
        if(targetId==null)throw new IllegalArgumentException("연결된 중복 후보 업무가 없습니다.");
        TodoItem target=todos.find(targetId);
        if(target.projectId()!=candidate.projectId())throw new IllegalArgumentException("다른 프로젝트의 할 일에는 합칠 수 없습니다.");
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
            throw new StateConflictException("새 담당자를 정해야 하는 할 일입니다. 관리자가 먼저 담당자를 재배정해 주세요.");
        if(status != null && status.equals(before.taskStatus())) return;
        if(!todos.updateTaskStatus(before.id(),status))throw new StateConflictException("확정되어 담당자가 정상 배정된 할 일만 상태를 변경할 수 있습니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"STATUS_CHANGE",json.writeValueAsString(before),"{\"taskStatus\":\""+status+"\"}");}
        catch(Exception e){throw new IllegalStateException(e);}
        timeline.append(before.projectId(),"TODO_STATUS",before.title(),status,LocalDateTime.now(),"TODO",before.id());
    }

    /** The assignee asks a decision-maker to review the work - task_status is left as-is until approved. */
    @Transactional
    public void requestCompletion(TodoItem before,User actor){
        if("REASSIGNMENT_REQUIRED".equals(before.assignmentStatus()))
            throw new StateConflictException("새 담당자를 정해야 하는 할 일입니다. 관리자가 먼저 담당자를 재배정해 주세요.");
        if(!todos.requestCompletion(before.id()))
            throw new StateConflictException("완료 요청할 수 없는 상태입니다.");
        timeline.append(before.projectId(),"TODO_COMPLETION_REQUESTED",before.title(),null,LocalDateTime.now(),"TODO",before.id());
    }

    /** Approving is what actually finishes the todo - deletes the calendar hold the same way a direct DONE used to. */
    @Transactional
    public void approveCompletion(TodoItem before,User actor){
        if(!todos.approveCompletion(before.id()))
            throw new StateConflictException("완료 승인 대기 중인 할 일이 아닙니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"COMPLETION_APPROVED",json.writeValueAsString(before),"{\"taskStatus\":\"DONE\"}");}
        catch(Exception e){throw new IllegalStateException(e);}
        timeline.append(before.projectId(),"TODO_STATUS",before.title(),"DONE",LocalDateTime.now(),"TODO",before.id());
        if(before.googleCalendarEventId()!=null&&before.assigneeId()!=null){
            calendar.deleteEvent(before.assigneeId(),before.googleCalendarEventId());
            todos.setCalendarEventId(before.id(),null);
        }
    }

    @Transactional
    public void rejectCompletion(TodoItem before,String reason,User actor){
        if(!todos.rejectCompletion(before.id(),reason))
            throw new StateConflictException("완료 승인 대기 중인 할 일이 아닙니다.");
        try{revisions.add(before.projectId(),"TODO",before.id(),actor.id(),"COMPLETION_REJECTED",json.writeValueAsString(before),
                json.writeValueAsString(java.util.Map.of("reason",reason==null?"":reason)));}
        catch(Exception e){throw new IllegalStateException(e);}
        timeline.append(before.projectId(),"TODO_COMPLETION_REJECTED",before.title(),reason,LocalDateTime.now(),"TODO",before.id());
    }

    @Transactional
    public void requestHelp(TodoItem before,String note,User actor){
        if(note==null||note.isBlank())throw new IllegalArgumentException("어떤 도움이 필요한지 적어 주세요.");
        if("REASSIGNMENT_REQUIRED".equals(before.assignmentStatus()))
            throw new StateConflictException("새 담당자를 정해야 하는 할 일입니다. 관리자가 먼저 담당자를 재배정해 주세요.");
        if(!todos.requestHelp(before.id(),note.trim()))
            throw new StateConflictException("도움을 요청할 수 없는 상태입니다.");
        timeline.append(before.projectId(),"TODO_HELP_REQUESTED",before.title(),note.trim(),LocalDateTime.now(),"TODO",before.id());
    }

    @Transactional
    public void resolveHelp(TodoItem before,User actor){
        if(!todos.resolveHelp(before.id()))
            throw new StateConflictException("도움을 요청한 상태의 할 일이 아닙니다.");
        timeline.append(before.projectId(),"TODO_STATUS",before.title(),"IN_PROGRESS",LocalDateTime.now(),"TODO",before.id());
    }
}
