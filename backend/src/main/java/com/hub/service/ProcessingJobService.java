// queue facade centralizes deterministic request keys so document/meeting retries cannot create duplicate AI work.
package com.hub.service;

import com.hub.repository.ProcessingJobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ProcessingJobService {
    private final ProcessingJobRepository jobs;
    private final ProcessingJobExecutor executor;

    public ProcessingJobService(ProcessingJobRepository jobs, ProcessingJobExecutor executor) {
        this.jobs = jobs; this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        jobs.failInterruptedJobs(); // same request_key can then be safely revived by the next retry.
    }

    public long queueDocument(long projectId, long versionId, LocalDate sourceDate) {
        String requestKey = "DOCUMENT_ANALYSIS:" + versionId;
        var lease = jobs.createOrReuse(projectId, "DOCUMENT_ANALYSIS", "DOCUMENT_VERSION", versionId, requestKey);
        if (lease.shouldRun()) executor.document(lease.id(), projectId, versionId, sourceDate);
        return lease.id();
    }

    public long queueMeeting(long projectId, long meetingId) {
        String requestKey = "MEETING_STT_ANALYSIS:" + meetingId;
        var lease = jobs.createOrReuse(projectId, "MEETING_STT_ANALYSIS", "MEETING", meetingId, requestKey);
        if (lease.shouldRun()) executor.meeting(lease.id(), projectId, meetingId);
        return lease.id();
    }
}
