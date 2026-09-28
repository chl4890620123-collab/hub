package com.hub.service;

import com.hub.model.TimelineEvent;
import com.hub.model.TodoItem;
import com.hub.repository.EvidenceRepository;
import com.hub.repository.TimelineRepository;
import com.hub.repository.TodoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** A read-only projection over Hub's authoritative workflow records. */
@Service
public class WorkflowFoundationService {
    public record Resource(String uri, String type, String title, String state) {}
    public record Relation(String from, String predicate, String to) {}
    public record Event(String uri, String type, String resourceUri, String happenedAt) {}
    public record Knowledge(String uri, String resourceUri, String excerpt, String sourceUri) {}
    public record Agent(String recommendedAction, String reason, boolean requiresHumanApproval) {}
    public record Snapshot(Resource resource, List<Relation> relations, List<Event> events,
                           List<Knowledge> knowledge, Agent agent) {}

    private final TodoRepository todos;
    private final EvidenceRepository evidence;
    private final TimelineRepository timeline;
    private final JdbcTemplate jdbc;

    public WorkflowFoundationService(TodoRepository todos, EvidenceRepository evidence,
                                     TimelineRepository timeline, JdbcTemplate jdbc) {
        this.todos = todos;
        this.evidence = evidence;
        this.timeline = timeline;
        this.jdbc = jdbc;
    }

    public Snapshot todo(long projectId, long todoId) {
        TodoItem todo = todos.find(todoId);
        if (todo.projectId() != projectId || todo.deletedAt() != null)
            throw new IllegalArgumentException("프로젝트에서 할 일을 찾을 수 없습니다.");

        String uri = uri(projectId, "todos", todoId);
        List<Relation> relations = new ArrayList<>();
        List<Long> meetings = jdbc.query("SELECT source_meeting_id FROM todo WHERE id=? AND project_id=? AND source_meeting_id IS NOT NULL",
                (rs, row) -> rs.getLong(1), todoId, projectId);
        if (!meetings.isEmpty()) relations.add(new Relation(uri, "DERIVED_FROM", uri(projectId, "meetings", meetings.get(0))));
        if (todo.assigneeId() != null && "CONFIRMED".equals(todo.reviewStatus())
                && "ACTIVE".equals(todo.assignmentStatus()))
            relations.add(new Relation(uri, "ASSIGNED_TO", uri(projectId, "members", todo.assigneeId())));

        List<Knowledge> knowledge = new ArrayList<>();
        for (EvidenceRepository.EvidenceView item : evidence.forTodo(todoId)) {
            String source = item.versionId() != null ? uri(projectId, "document-versions", item.versionId()) : null;
            knowledge.add(new Knowledge(uri(projectId, "evidence", item.id()), uri, item.quote(), source));
            relations.add(new Relation(uri, "SUPPORTED_BY", uri(projectId, "evidence", item.id())));
        }

        List<Event> events = new ArrayList<>();
        for (TimelineEvent event : timeline.listForSource(projectId, "TODO", todoId, 200))
            events.add(new Event(uri(projectId, "events", event.id()), event.eventType(), uri, event.happenedAt().toString()));
        return new Snapshot(new Resource(uri, "TODO", todo.title(), todo.taskStatus()),
                List.copyOf(relations), List.copyOf(events), List.copyOf(knowledge), next(todo));
    }

    static Agent next(TodoItem todo) {
        if ("REJECTED".equals(todo.reviewStatus()))
            return new Agent("NONE", "제외된 업무 후보입니다.", false);
        if (!"CONFIRMED".equals(todo.reviewStatus()))
            return new Agent("ADMIN_REVIEW", "관리자가 후보 업무와 담당자를 확인해야 합니다.", true);
        if ("REASSIGNMENT_REQUIRED".equals(todo.assignmentStatus()))
            return new Agent("ADMIN_REASSIGN", "담당자를 다시 지정해야 합니다.", true);
        if (todo.pendingApproval())
            return new Agent("ADMIN_REVIEW_COMPLETION", "담당자의 제출물을 관리자가 검토해야 합니다.", true);
        if ("DONE".equals(todo.taskStatus()))
            return new Agent("NONE", "완료 승인된 업무입니다.", false);
        if ("BLOCKED".equals(todo.taskStatus()))
            return new Agent("ASSIGNEE_RESOLVE_HELP", "담당자가 도움 요청을 해결한 뒤 작업을 이어갈 수 있습니다.", false);
        if (todo.statusNote() != null && !todo.statusNote().isBlank())
            return new Agent("ASSIGNEE_RESUME", "보류 또는 반려 사유를 확인하고 진행 상태로 되돌려야 합니다.", false);
        if ("TODO".equals(todo.taskStatus()))
            return new Agent("ASSIGNEE_START", "담당자가 진행 상태로 바꾼 뒤 작업을 제출할 수 있습니다.", false);
        return new Agent("ASSIGNEE_WORK_AND_SUBMIT", "담당자가 작업 후 파일 또는 URL로 완료를 요청할 수 있습니다.", false);
    }

    private static String uri(long projectId, String type, long id) {
        return "hub://projects/" + projectId + "/" + type + "/" + id;
    }
}
