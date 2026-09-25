// Spring is the typed boundary that normalizes Python snake_case into Java/browser camelCase without duplicate mapping logic.
package com.hub.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class AiDtos {
    private AiDtos() {}

    // id is Java-side only: the AI service response never carries one (Jackson just leaves it null),
    // it is filled in after AnalysisService persists the grounded proposal as a real TodoItem, so the
    // browser can act on that specific candidate (edit/assign/reject) right where it first sees it
    // instead of having to look it up on a different screen.
    public record TodoProposal(
            Long id,
            String title,
            String description,
            @JsonAlias("assignee_text") String assigneeText,
            @JsonAlias("assignee_suggestion_text") String assigneeSuggestionText,
            @JsonAlias("due_date") String dueDate,
            @JsonAlias("due_date_suggestion") String dueDateSuggestion,
            String confidence,
            @JsonAlias("evidence_quote") String evidenceQuote) {}

    public record DecisionProposal(String statement, String confidence,
                                   @JsonAlias("evidence_quote") String evidenceQuote) {}

    public record MemberCandidate(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("login_id") String loginId,
            @JsonProperty("job_title") String jobTitle) {}

    public record AnalyzeResponse(String summary, List<TodoProposal> todos, List<DecisionProposal> decisions) {}
    public record RagChunk(long id, String text, @JsonProperty("paragraph_ref") String paragraphRef) {}
    public record RagEvidence(long id, String quote) {}
    public record RagResponse(String answer, List<RagEvidence> evidence) {}
    public record ChangeItem(String category, String before, String after, String reason) {}
    public record ChangeResponse(List<ChangeItem> changes) {}
    public record ReviseResponse(String revisedText) {}
    public record SttResponse(String text, List<SttSegment> segments) {}
    public record EmbedResponse(String model, int dimensions, List<List<Float>> vectors) {}
    public record SttSegment(@JsonAlias("start_ms") Long startMs, @JsonAlias("end_ms") Long endMs,
                             String speaker, String text) {}
}
