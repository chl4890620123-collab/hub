// background executor keeps slow AI/STT calls out of request threads while preserving one job per target.
package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.ProcessingJobRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

@Service
public class ProcessingJobExecutor {
    private final ProcessingJobRepository jobs;
    private final AnalysisService analysis;
    private final MeetingService meetings;
    private final ConnectorService connectors;
    private final MaterialSearchService materials;
    private final DocumentService documents;
    private final AuditRepository audit;
    private final ObjectMapper json;

    public ProcessingJobExecutor(ProcessingJobRepository jobs, AnalysisService analysis, MeetingService meetings,
                                 ConnectorService connectors, MaterialSearchService materials, DocumentService documents,
                                 AuditRepository audit, ObjectMapper json) {
        this.jobs = jobs; this.analysis = analysis; this.meetings = meetings; this.connectors = connectors;
        this.materials = materials; this.documents = documents; this.audit = audit; this.json = json;
    }

    @Async("hubTaskExecutor")
    public void document(long jobId, long projectId, long versionId, LocalDate sourceDate) {
        try {
            jobs.start(jobId); jobs.progress(jobId, 20);
            var result = analysis.analyzeDocument(projectId, versionId, sourceDate);
            jobs.progress(jobId, 90);
            jobs.success(jobId, json.writeValueAsString(result));
        } catch (Exception error) {
            jobs.fail(jobId, "DOCUMENT_ANALYSIS_FAILED", rootMessage(error));
        }
    }

    @Async("hubTaskExecutor")
    public void meeting(long jobId, long projectId, long meetingId) {
        try {
            jobs.start(jobId); jobs.progress(jobId, 10);
            var result = meetings.processStored(meetingId, progress -> jobs.progress(jobId, progress));
            jobs.progress(jobId, 95);
            jobs.success(jobId, json.writeValueAsString(result.analysis()));
        } catch (Exception error) {
            meetings.markFailed(meetingId);
            jobs.fail(jobId, "MEETING_PROCESSING_FAILED", rootMessage(error));
        }
    }

    @Async("hubTaskExecutor")
    public void materialAsk(long jobId, long projectId, String question, User user) {
        try {
            jobs.start(jobId); jobs.progress(jobId, 15);
            var result = materials.ask(projectId, question, user);
            jobs.progress(jobId, 90);
            jobs.success(jobId, json.writeValueAsString(result));
        } catch (Exception error) {
            jobs.fail(jobId, "MATERIAL_ASK_FAILED", rootMessage(error));
        }
    }

    @Async("hubTaskExecutor")
    public void documentRevision(long jobId, long projectId, long documentId, long meetingDocumentId, User user) {
        try {
            jobs.start(jobId); jobs.progress(jobId, 20);
            String revisedText = documents.reviseDraftFromMeeting(projectId, documentId, meetingDocumentId, user);
            jobs.progress(jobId, 90);
            jobs.success(jobId, json.writeValueAsString(Map.of("revisedText", revisedText)));
        } catch (Exception error) {
            jobs.fail(jobId, "DOCUMENT_REVISION_FAILED", rootMessage(error));
        }
    }

    @Async("hubTaskExecutor")
    public void connectorImport(long jobId, long projectId, String type, String scope, User user) {
        try {
            jobs.start(jobId); jobs.progress(jobId, 20);
            int imported = connectors.importItems(projectId, type, scope, user);
            jobs.progress(jobId, 90);
            jobs.success(jobId, json.writeValueAsString(Map.of("imported", imported)));
            audit.add(user.id(), projectId, "CONNECTOR_IMPORT", type == null ? null : type.trim().toUpperCase(Locale.ROOT),
                    null, "{\"imported\":" + imported + "}");
        } catch (Exception error) {
            jobs.fail(jobId, "CONNECTOR_IMPORT_FAILED", rootMessage(error));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
        return friendly(message);
    }

    /**
     * The provider's own wording reaches this screen, so a spent quota showed up as a raw
     * 502/429 dump. Operators only need to know that the AI is unavailable and why.
     */
    private static String friendly(String raw) {
        if (raw == null) return "AI 처리에 실패했습니다.";
        if (raw.contains("429") || raw.contains("Too Many Requests") || raw.contains("RESOURCE_EXHAUSTED"))
            return "AI 사용량 한도를 초과했습니다. 잠시 후 다시 시도하거나 사용 중인 AI 요금제를 확인해 주세요.";
        if (raw.contains("503") || raw.contains("UNAVAILABLE") || raw.contains("overloaded"))
            return "AI 서비스가 일시적으로 혼잡합니다. 잠시 후 다시 시도해 주세요.";
        if (raw.contains("timed out") || raw.contains("ReadTimeout") || raw.contains("timeout"))
            return "AI 응답이 지연되어 처리를 마치지 못했습니다. 잠시 후 다시 시도해 주세요.";
        return raw;
    }
}
