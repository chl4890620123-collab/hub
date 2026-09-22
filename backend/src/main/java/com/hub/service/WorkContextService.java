package com.hub.service;

import com.hub.model.MaterialAskResponse;
import com.hub.model.MaterialHit;
import com.hub.model.TimelineEvent;
import com.hub.model.TodoItem;
import com.hub.model.WorkContextBundle;
import com.hub.repository.ChangeRepository;
import com.hub.repository.DecisionRepository;
import com.hub.repository.TimelineRepository;
import com.hub.repository.TodoRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class WorkContextService {
    private final MaterialSearchService materials;
    private final TodoRepository todos;
    private final DecisionRepository decisions;
    private final ChangeRepository changes;
    private final TimelineRepository timeline;

    public WorkContextService(MaterialSearchService materials,
                              TodoRepository todos,
                              DecisionRepository decisions,
                              ChangeRepository changes,
                              TimelineRepository timeline) {
        this.materials = materials;
        this.todos = todos;
        this.decisions = decisions;
        this.changes = changes;
        this.timeline = timeline;
    }

    /**
     * Connects heterogeneous sources into one work context. AI writes only the summary; official
     * TODO/decision/change records are read from Hub DB and keep their review state.
     */
    public WorkContextBundle build(long projectId, String query) {
        String q = required(query);
        List<MaterialHit> sources = materials.search(projectId, q).stream().limit(12).toList();
        MaterialAskResponse ask;
        try {
            ask = materials.ask(projectId, q);
        } catch (RuntimeException unavailableAi) {
            String fallback = sources.isEmpty()
                    ? "관련 자료에서 해당 업무 맥락을 확인하지 못했습니다."
                    : "관련 자료 " + sources.size() + "건을 찾았습니다. AI 요약 서비스가 응답하지 않아 원본 위치와 업무 기록을 우선 표시합니다.";
            ask = new MaterialAskResponse(fallback, sources);
        }
        Set<String> terms = terms(q, sources);
        List<TodoItem> relatedTodos = todos.listRecentConfirmed(projectId, 100).stream()
                .filter(todo -> containsAny(todo.title() + " " + safe(todo.description()), terms))
                .limit(12).toList();
        List<Map<String, Object>> relatedDecisions = decisions.listConfirmed(projectId, 100).stream()
                .filter(row -> containsAny(text(row, "statement"), terms)).limit(12).toList();
        List<Map<String, Object>> relatedChanges = changes.listConfirmed(projectId, 100).stream()
                .filter(row -> containsAny(text(row, "category") + " " + text(row, "before_text") + " " + text(row, "after_text") + " " + text(row, "reason"), terms))
                .limit(12).toList();
        List<TimelineEvent> relatedTimeline = timeline.list(projectId, 100).stream()
                .filter(row -> containsAny(row.title() + " " + safe(row.description()), terms)).limit(12).toList();
        return new WorkContextBundle(q, ask.answer(), sources, relatedTodos, relatedDecisions, relatedChanges, relatedTimeline);
    }

    private static String required(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isBlank()) throw new IllegalArgumentException("찾을 내용을 입력해 주세요.");
        return v;
    }

    private static Set<String> terms(String query, List<MaterialHit> sources) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        tokenize(query, result);
        for (MaterialHit source : sources.stream().limit(5).toList()) tokenize(source.title(), result);
        return result;
    }

    private static void tokenize(String text, Set<String> out) {
        if (text == null) return;
        for (String token : text.split("[^\\p{L}\\p{N}_-]+")) {
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (value.length() >= 2) out.add(value);
            if (out.size() >= 20) return;
        }
    }

    private static boolean containsAny(String text, Set<String> terms) {
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return false;
        for (String term : terms) if (normalized.contains(term)) return true;
        return false;
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(Locale.ROOT));
        return value == null ? "" : value.toString();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
