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
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }
}
