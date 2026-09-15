// background executor keeps slow AI/STT calls out of request threads while preserving one job per target.
package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.repository.ProcessingJobRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ProcessingJobExecutor {
    private final ProcessingJobRepository jobs;
    private final AnalysisService analysis;
    private final MeetingService meetings;
    private final ObjectMapper json;

    public ProcessingJobExecutor(ProcessingJobRepository jobs, AnalysisService analysis, MeetingService meetings, ObjectMapper json) {
        this.jobs = jobs; this.analysis = analysis; this.meetings = meetings; this.json = json;
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
