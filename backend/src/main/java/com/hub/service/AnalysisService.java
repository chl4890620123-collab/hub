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
        AiDtos.AnalyzeResponse raw = requireAnalysis(ai.analyze(text, dateText(sourceDate)));
        List<SearchHit> chunks = documents.chunksForVersion(versionId);
        List<GroundedTodo> groundedTodos = groundDocumentTodos(raw, chunks);
        List<GroundedDecision> groundedDecisions = groundDocumentDecisions(raw, chunks);
        AiDtos.AnalyzeResponse groundedResponse = response(raw, groundedTodos, groundedDecisions);

        AiDtos.AnalyzeResponse saved = transaction.execute(status -> {
            documents.lockVersion(versionId);
            var existing = aiRuns.successfulDocument(versionId, DOCUMENT_ANALYSIS);
            if (existing.isPresent()) return decode(existing.get());

            documents.updateSummary(versionId, raw.summary());
            persistDocumentTodos(projectId, versionId, groundedTodos);
            persistDocumentDecisions(projectId, versionId, groundedDecisions);
            aiRuns.saveDocument(projectId, versionId, DOCUMENT_ANALYSIS, encode(groundedResponse));
            return groundedResponse;
        });
        if (saved == null) throw new IllegalStateException("Document analysis transaction returned no result");
        return saved;
    }

    /** Same idempotency rule as document analysis, but grounded to immutable transcript segments. */
    public AiDtos.AnalyzeResponse analyzeMeeting(long projectId, long meetingId, LocalDate sourceDate) {
        var cached = aiRuns.successfulMeeting(meetingId, MEETING_ANALYSIS);
        if (cached.isPresent()) return decode(cached.get());

        String text = meetings.transcript(meetingId);
        AiDtos.AnalyzeResponse raw = requireAnalysis(ai.analyze(text, dateText(sourceDate)));
        List<Map<String, Object>> segments = meetings.segments(meetingId);
        List<GroundedMeetingTodo> groundedTodos = groundMeetingTodos(raw, segments);
        List<GroundedMeetingDecision> groundedDecisions = groundMeetingDecisions(raw, segments);
        AiDtos.AnalyzeResponse groundedResponse = new AiDtos.AnalyzeResponse(
                raw.summary(),
                groundedTodos.stream().map(GroundedMeetingTodo::proposal).toList(),
                groundedDecisions.stream().map(GroundedMeetingDecision::proposal).toList()
        );

        AiDtos.AnalyzeResponse saved = transaction.execute(status -> {
            meetings.lockMeeting(meetingId);
            var existing = aiRuns.successfulMeeting(meetingId, MEETING_ANALYSIS);
            if (existing.isPresent()) return decode(existing.get());

            persistMeetingTodos(projectId, meetingId, groundedTodos);
            persistMeetingDecisions(projectId, meetingId, groundedDecisions);
            aiRuns.saveMeeting(projectId, meetingId, MEETING_ANALYSIS, encode(groundedResponse));
            return groundedResponse;
        });
        if (saved == null) throw new IllegalStateException("Meeting analysis transaction returned no result");
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

    private void persistDocumentTodos(long projectId, long versionId, List<GroundedTodo> groundedTodos) {
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
        }
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

    private void persistMeetingTodos(long projectId,
                                     long meetingId,
                                     List<GroundedMeetingTodo> groundedTodos) {
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
        }
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

    private AiDtos.AnalyzeResponse response(AiDtos.AnalyzeResponse raw,
                                            List<GroundedTodo> todos,
                                            List<GroundedDecision> decisions) {
        return new AiDtos.AnalyzeResponse(
                raw.summary(),
                todos.stream().map(GroundedTodo::proposal).toList(),
                decisions.stream().map(GroundedDecision::proposal).toList()
        );
    }

    private AiDtos.AnalyzeResponse requireAnalysis(AiDtos.AnalyzeResponse response) {
        if (response == null) throw new IllegalStateException("AI service returned no analysis");
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
     * Links the name the AI read out of the document to a real project member, so the review screen can
     * preselect that person. Only the suggestion is stored; assignee_id stays empty until an admin confirms.
     * An ambiguous name (two members sharing a display name) resolves to nothing rather than to a guess.
     */
    private Long matchProjectMember(long projectId, String suggestedName) {
        if (suggestedName == null || suggestedName.isBlank()) return null;
        String wanted = normalizePersonName(suggestedName);
        if (wanted.isEmpty()) return null;
        Long matched = null;
        for (Map<String, Object> member : projects.listMembers(projectId)) {
            Object nameValue = value(member, "display_name");
            if (nameValue == null || !wanted.equals(normalizePersonName(nameValue.toString()))) continue;
            Object idValue = value(member, "user_id");
            if (!(idValue instanceof Number number)) continue;
            if (matched != null && matched != number.longValue()) return null;
            matched = number.longValue();
        }
        return matched;
    }

    private static String normalizePersonName(String raw) {
        return raw.replaceAll("\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private String candidateAssigneeText(AiDtos.TodoProposal proposal) {
        if (proposal.assigneeText() != null && !proposal.assigneeText().isBlank()) {
            return proposal.assigneeText().trim();
        }
        if (proposal.assigneeSuggestionText() != null && !proposal.assigneeSuggestionText().isBlank()) {
            return proposal.assigneeSuggestionText().trim();
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
            throw new IllegalStateException("Failed to serialize analysis result", error);
        }
    }

    private AiDtos.AnalyzeResponse decode(String value) {
        try {
            return json.readValue(value, AiDtos.AnalyzeResponse.class);
        } catch (Exception error) {
            throw new IllegalStateException("Stored analysis result is invalid", error);
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
