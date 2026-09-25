package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.dto.AiDtos;
import com.hub.model.SearchHit;
import com.hub.repository.AiRunRepository;
import com.hub.repository.DecisionRepository;
import com.hub.repository.DocumentRepository;
import com.hub.repository.EvidenceRepository;
import com.hub.repository.MeetingRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.TimelineRepository;
import com.hub.repository.TodoRepository;
import com.hub.util.Hashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisService {
    private static final String DOCUMENT_ANALYSIS = "DOCUMENT_ANALYSIS";
    private static final String MEETING_ANALYSIS = "MEETING_ANALYSIS";

    private final AiClient ai;
    private final DocumentRepository documents;
    private final TodoRepository todos;
    private final TodoDuplicateDetector duplicateDetector;
    private final DecisionRepository decisions;
    private final EvidenceRepository evidence;
    private final TimelineRepository timeline;
    private final MeetingRepository meetings;
    private final ProjectRepository projects;
    private final AiRunRepository aiRuns;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;

    public AnalysisService(AiClient ai,
                           DocumentRepository documents,
                           TodoRepository todos, TodoDuplicateDetector duplicateDetector,
                           DecisionRepository decisions,
                           EvidenceRepository evidence,
                           TimelineRepository timeline,
                           MeetingRepository meetings,
                           ProjectRepository projects,
                           AiRunRepository aiRuns,
                           ObjectMapper json,
                           org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.ai = ai;
        this.documents = documents;
        this.todos = todos;
        this.duplicateDetector = duplicateDetector;
        this.decisions = decisions;
        this.evidence = evidence;
        this.timeline = timeline;
        this.meetings = meetings;
        this.projects = projects;
        this.aiRuns = aiRuns;
        this.json = json;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * AI calls happen outside DB transactions. Persisting the grounded result is idempotent per
     * document version, so repeated button clicks cannot create duplicate TODOs or decisions.
     */
    public AiDtos.AnalyzeResponse analyzeDocument(long projectId, long versionId, LocalDate sourceDate) {
        var cached = aiRuns.successfulDocument(versionId, DOCUMENT_ANALYSIS);
        if (cached.isPresent()) return decode(cached.get());

        String text = documents.versionText(versionId);
        AiDtos.AnalyzeResponse raw = requireAnalysis(ai.analyze(text, dateText(sourceDate), memberCandidates(projectId)));
        List<SearchHit> chunks = documents.chunksForVersion(versionId);
        List<GroundedTodo> groundedTodos = groundDocumentTodos(raw, chunks);
        List<GroundedDecision> groundedDecisions = groundDocumentDecisions(raw, chunks);

        AiDtos.AnalyzeResponse saved = transaction.execute(status -> {
            documents.lockVersion(versionId);
            var existing = aiRuns.successfulDocument(versionId, DOCUMENT_ANALYSIS);
            if (existing.isPresent()) return decode(existing.get());

            documents.updateSummary(versionId, raw.summary());
            List<AiDtos.TodoProposal> persistedTodos = persistDocumentTodos(projectId, versionId, groundedTodos);
            persistDocumentDecisions(projectId, versionId, groundedDecisions);
            AiDtos.AnalyzeResponse groundedResponse = new AiDtos.AnalyzeResponse(
                    raw.summary(), persistedTodos, groundedDecisions.stream().map(GroundedDecision::proposal).toList());
            aiRuns.saveDocument(projectId, versionId, DOCUMENT_ANALYSIS, encode(groundedResponse));
            return groundedResponse;
        });
        if (saved == null) throw new IllegalStateException("문서 분석 결과를 저장하지 못했습니다.");
        return saved;
    }

    /** Same idempotency rule as document analysis, but grounded to immutable transcript segments. */
    public AiDtos.AnalyzeResponse analyzeMeeting(long projectId, long meetingId, LocalDate sourceDate) {
        var cached = aiRuns.successfulMeeting(meetingId, MEETING_ANALYSIS);
        if (cached.isPresent()) return decode(cached.get());

        String text = meetings.transcript(meetingId);
        AiDtos.AnalyzeResponse raw = requireAnalysis(ai.analyze(text, dateText(sourceDate), memberCandidates(projectId)));
        List<Map<String, Object>> segments = meetings.segments(meetingId);
        List<GroundedMeetingTodo> groundedTodos = groundMeetingTodos(raw, segments);
        List<GroundedMeetingDecision> groundedDecisions = groundMeetingDecisions(raw, segments);

        AiDtos.AnalyzeResponse saved = transaction.execute(status -> {
            meetings.lockMeeting(meetingId);
            var existing = aiRuns.successfulMeeting(meetingId, MEETING_ANALYSIS);
            if (existing.isPresent()) return decode(existing.get());

            List<AiDtos.TodoProposal> persistedTodos = persistMeetingTodos(projectId, meetingId, groundedTodos);
            persistMeetingDecisions(projectId, meetingId, groundedDecisions);
            AiDtos.AnalyzeResponse groundedResponse = new AiDtos.AnalyzeResponse(
                    raw.summary(), persistedTodos, groundedDecisions.stream().map(GroundedMeetingDecision::proposal).toList());
            aiRuns.saveMeeting(projectId, meetingId, MEETING_ANALYSIS, encode(groundedResponse));
            return groundedResponse;
        });
        if (saved == null) throw new IllegalStateException("회의 분석 결과를 저장하지 못했습니다.");
        return saved;
    }

    private List<GroundedTodo> groundDocumentTodos(AiDtos.AnalyzeResponse raw, List<SearchHit> chunks) {
        List<GroundedTodo> result = new ArrayList<>();
        if (raw.todos() == null) return result;
        for (AiDtos.TodoProposal proposal : raw.todos()) {
            SearchHit hit = findDocumentEvidence(chunks, proposal.evidenceQuote());
            if (hit != null) result.add(new GroundedTodo(proposal, hit.chunkId()));
        }
        return result;
    }

    private List<GroundedDecision> groundDocumentDecisions(AiDtos.AnalyzeResponse raw, List<SearchHit> chunks) {
        List<GroundedDecision> result = new ArrayList<>();
        if (raw.decisions() == null) return result;
        for (AiDtos.DecisionProposal proposal : raw.decisions()) {
            SearchHit hit = findDocumentEvidence(chunks, proposal.evidenceQuote());
            if (hit != null) result.add(new GroundedDecision(proposal, hit.chunkId()));
        }
        return result;
    }

    private List<GroundedMeetingTodo> groundMeetingTodos(AiDtos.AnalyzeResponse raw,
                                                          List<Map<String, Object>> segments) {
        List<GroundedMeetingTodo> result = new ArrayList<>();
        if (raw.todos() == null) return result;
        for (AiDtos.TodoProposal proposal : raw.todos()) {
            Long segmentId = findMeetingEvidence(segments, proposal.evidenceQuote());
            if (segmentId != null) result.add(new GroundedMeetingTodo(proposal, segmentId));
        }
        return result;
    }

    private List<GroundedMeetingDecision> groundMeetingDecisions(AiDtos.AnalyzeResponse raw,
                                                                  List<Map<String, Object>> segments) {
        List<GroundedMeetingDecision> result = new ArrayList<>();
        if (raw.decisions() == null) return result;
        for (AiDtos.DecisionProposal proposal : raw.decisions()) {
            Long segmentId = findMeetingEvidence(segments, proposal.evidenceQuote());
            if (segmentId != null) result.add(new GroundedMeetingDecision(proposal, segmentId));
        }
        return result;
    }

    private List<AiDtos.TodoProposal> persistDocumentTodos(long projectId, long versionId, List<GroundedTodo> groundedTodos) {
        List<AiDtos.TodoProposal> result = new ArrayList<>();
        for (GroundedTodo grounded : groundedTodos) {
            AiDtos.TodoProposal proposal = grounded.proposal();
            String quote = proposal.evidenceQuote().trim();
            long evidenceId = evidence.createDocumentEvidence(
                    versionId, grounded.chunkId(), quote, Hashing.sha256(quote)
            );
            Long candidateId = matchProjectMember(projectId, candidateAssigneeText(proposal));
            TodoDuplicateDetector.DuplicateMatch duplicate = duplicateDetector.find(projectId, proposal.title());
            long todoId = todos.create(
                    projectId, versionId, null, proposal.title(), proposal.description(), proposal.assigneeText(),
                    candidateId, candidateAssigneeText(proposal), null,
                    firstDate(proposal.dueDate(), proposal.dueDateSuggestion()), proposal.confidence(),
                    duplicate.todoId(), duplicate.reason()
            );
            evidence.linkTodo(todoId, evidenceId);
            timeline.append(
                    projectId, "TODO_CREATED", proposal.title(), proposal.description(),
                    LocalDateTime.now(), "DOCUMENT_VERSION", versionId
            );
            result.add(withId(proposal, todoId));
        }
        return result;
    }

    private void persistDocumentDecisions(long projectId,
                                          long versionId,
                                          List<GroundedDecision> groundedDecisions) {
        for (GroundedDecision grounded : groundedDecisions) {
            AiDtos.DecisionProposal proposal = grounded.proposal();
            String quote = proposal.evidenceQuote().trim();
            long evidenceId = evidence.createDocumentEvidence(
                    versionId, grounded.chunkId(), quote, Hashing.sha256(quote)
            );
            long decisionId = decisions.create(
                    projectId, versionId, null, proposal.statement(), proposal.confidence()
            );
            decisions.linkEvidence(decisionId, evidenceId);
            timeline.append(
                    projectId, "DECISION_CANDIDATE", proposal.statement(), null,
                    LocalDateTime.now(), "DOCUMENT_VERSION", versionId
            );
        }
    }

    private List<AiDtos.TodoProposal> persistMeetingTodos(long projectId,
                                     long meetingId,
                                     List<GroundedMeetingTodo> groundedTodos) {
        List<AiDtos.TodoProposal> result = new ArrayList<>();
        for (GroundedMeetingTodo grounded : groundedTodos) {
            AiDtos.TodoProposal proposal = grounded.proposal();
            String quote = proposal.evidenceQuote().trim();
            long evidenceId = evidence.createTranscriptEvidence(
                    grounded.segmentId(), quote, Hashing.sha256(quote)
            );
            Long candidateId = matchProjectMember(projectId, candidateAssigneeText(proposal));
            TodoDuplicateDetector.DuplicateMatch duplicate = duplicateDetector.find(projectId, proposal.title());
            long todoId = todos.create(
                    projectId, null, meetingId, proposal.title(), proposal.description(), proposal.assigneeText(),
                    candidateId, candidateAssigneeText(proposal), null,
                    firstDate(proposal.dueDate(), proposal.dueDateSuggestion()), proposal.confidence(),
                    duplicate.todoId(), duplicate.reason()
            );
            evidence.linkTodo(todoId, evidenceId);
            timeline.append(
                    projectId, "TODO_CREATED", proposal.title(), proposal.description(),
                    LocalDateTime.now(), "MEETING", meetingId
            );
            result.add(withId(proposal, todoId));
        }
        return result;
    }

    private void persistMeetingDecisions(long projectId,
                                         long meetingId,
                                         List<GroundedMeetingDecision> groundedDecisions) {
        for (GroundedMeetingDecision grounded : groundedDecisions) {
            AiDtos.DecisionProposal proposal = grounded.proposal();
            String quote = proposal.evidenceQuote().trim();
            long evidenceId = evidence.createTranscriptEvidence(
                    grounded.segmentId(), quote, Hashing.sha256(quote)
            );
            long decisionId = decisions.create(
                    projectId, null, meetingId, proposal.statement(), proposal.confidence()
            );
            decisions.linkEvidence(decisionId, evidenceId);
            timeline.append(
                    projectId, "DECISION_CANDIDATE", proposal.statement(), null,
                    LocalDateTime.now(), "MEETING", meetingId
            );
        }
    }

    /** Copies a proposal with its now-persisted todo id attached, so the browser can act on this
     * exact candidate (edit/assign/reject) without a separate lookup. */
    private static AiDtos.TodoProposal withId(AiDtos.TodoProposal proposal, long id) {
        return new AiDtos.TodoProposal(id, proposal.title(), proposal.description(), proposal.assigneeText(),
                proposal.assigneeSuggestionText(), proposal.dueDate(), proposal.dueDateSuggestion(),
                proposal.confidence(), proposal.evidenceQuote());
    }

    private AiDtos.AnalyzeResponse requireAnalysis(AiDtos.AnalyzeResponse response) {
        if (response == null) throw new IllegalStateException("AI 분석 결과를 받지 못했습니다. 다시 시도해 주세요.");
        return response;
    }

    private SearchHit findDocumentEvidence(List<SearchHit> chunks, String quote) {
        String safeQuote = quote == null ? "" : quote.trim();
        if (safeQuote.isBlank()) return null;
        return chunks.stream().filter(chunk -> chunk.content().contains(safeQuote)).findFirst().orElse(null);
    }

    private Long findMeetingEvidence(List<Map<String, Object>> segments, String quote) {
        String safeQuote = quote == null ? "" : quote.trim();
        if (safeQuote.isBlank()) return null;
        for (Map<String, Object> row : segments) {
            Object textValue = value(row, "text");
            String segmentText = textValue == null ? "" : textValue.toString();
            if (segmentText.contains(safeQuote)) {
                Object idValue = value(row, "id");
                if (idValue instanceof Number number) return number.longValue();
            }
        }
        return null;
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) return row.get(key);
        return row.get(key.toUpperCase());
    }

    /**
     * Links the AI's roster-constrained suggestion to a real project member, so the review screen can
     * preselect that person. assignee_id still stays empty until a decision-maker confirms the candidate.
     * Matching accepts the exact display name or login id only; ambiguous matches resolve to nothing.
     */
    private Long matchProjectMember(long projectId, String suggestedName) {
        if (suggestedName == null || suggestedName.isBlank()) return null;
        String wanted = normalizePersonName(suggestedName);
        if (wanted.isEmpty()) return null;
        Long matched = null;
        for (Map<String, Object> member : projects.listMembers(projectId)) {
            Object nameValue = value(member, "display_name");
            Object loginValue = value(member, "login_id");
            boolean sameDisplay = nameValue != null && wanted.equals(normalizePersonName(nameValue.toString()));
            boolean sameLogin = loginValue != null && wanted.equals(normalizePersonName(loginValue.toString()));
            if (!sameDisplay && !sameLogin) continue;
            Object idValue = value(member, "user_id");
            if (!(idValue instanceof Number number)) continue;
            if (matched != null && matched != number.longValue()) return null;
            matched = number.longValue();
        }
        return matched;
    }

    private List<AiDtos.MemberCandidate> memberCandidates(long projectId) {
        List<AiDtos.MemberCandidate> result = new ArrayList<>();
        for (Map<String,Object> member : projects.listMembers(projectId)) {
            Object displayName = value(member, "display_name");
            if (displayName == null || displayName.toString().isBlank()) continue;
            Object loginId = value(member, "login_id");
            Object jobTitle = value(member, "job_title");
            result.add(new AiDtos.MemberCandidate(
                    displayName.toString(),
                    loginId == null ? null : loginId.toString(),
                    jobTitle == null ? null : jobTitle.toString()
            ));
        }
        return result;
    }

    private static String normalizePersonName(String raw) {
        return raw.replaceAll("\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private String candidateAssigneeText(AiDtos.TodoProposal proposal) {
        // The roster-constrained suggestion is the value intended for member-ID linking.
        // Keep the raw assignee_text only as a fallback/evidence when no safe roster match was proposed.
        if (proposal.assigneeSuggestionText() != null && !proposal.assigneeSuggestionText().isBlank()) {
            return proposal.assigneeSuggestionText().trim();
        }
        if (proposal.assigneeText() != null && !proposal.assigneeText().isBlank()) {
            return proposal.assigneeText().trim();
        }
        return null;
    }

    private LocalDate firstDate(String primary,String secondary){LocalDate p=parseDate(primary);return p!=null?p:parseDate(secondary);}

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String dateText(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private String encode(AiDtos.AnalyzeResponse response) {
        try {
            return json.writeValueAsString(response);
        } catch (Exception error) {
            throw new IllegalStateException("AI 분석 결과를 저장 형식으로 변환하지 못했습니다.", error);
        }
    }

    private AiDtos.AnalyzeResponse decode(String value) {
        try {
            return json.readValue(value, AiDtos.AnalyzeResponse.class);
        } catch (Exception error) {
            throw new IllegalStateException("저장된 AI 분석 결과를 읽지 못했습니다.", error);
        }
    }

    private record GroundedTodo(AiDtos.TodoProposal proposal, long chunkId) {
    }

    private record GroundedDecision(AiDtos.DecisionProposal proposal, long chunkId) {
    }

    private record GroundedMeetingTodo(AiDtos.TodoProposal proposal, long segmentId) {
    }

    private record GroundedMeetingDecision(AiDtos.DecisionProposal proposal, long segmentId) {
    }
}
