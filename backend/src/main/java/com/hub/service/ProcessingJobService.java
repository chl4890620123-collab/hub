// queue facade centralizes deterministic request keys so document/meeting retries cannot create duplicate AI work.
package com.hub.service;

import com.hub.model.User;
import com.hub.repository.ProcessingJobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;

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
        return queueDocument(projectId, versionId, sourceDate, false);
    }

    public long queueDocument(long projectId, long versionId, LocalDate sourceDate, boolean force) {
        String requestKey = "DOCUMENT_ANALYSIS:" + versionId;
        var lease = force
                ? jobs.createOrReuseRerunnable(projectId, "DOCUMENT_ANALYSIS", "DOCUMENT_VERSION", versionId, requestKey)
                : jobs.createOrReuse(projectId, "DOCUMENT_ANALYSIS", "DOCUMENT_VERSION", versionId, requestKey);
        if (lease.shouldRun()) executor.document(lease.id(), projectId, versionId, sourceDate);
        return lease.id();
    }

    public long queueMeeting(long projectId, long meetingId) {
        String requestKey = "MEETING_STT_ANALYSIS:" + meetingId;
        var lease = jobs.createOrReuse(projectId, "MEETING_STT_ANALYSIS", "MEETING", meetingId, requestKey);
        if (lease.shouldRun()) executor.meeting(lease.id(), projectId, meetingId);
        return lease.id();
    }

    /**
     * Import has no single row to key on the way a document version or meeting does - its identity
     * is the (project, connector type, scope) triple, so that goes into the request key instead of
     * target_id (left at 0, unused for this job type). Uses createOrReuseRerunnable, not
     * createOrReuse: "자료 가져오기" is meant to be clicked again to re-sync, so a stable key must
     * revive on SUCCESS too, not only FAILED - otherwise every click after the first success would
     * silently hand back that first run's result forever instead of importing anything.
     */
    public long queueConnectorImport(long projectId, String type, String scope, User user) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        String requestKey = "CONNECTOR_IMPORT:" + projectId + ":" + normalizedType + ":" + (scope == null ? "" : scope.trim());
        var lease = jobs.createOrReuseRerunnable(projectId, "CONNECTOR_IMPORT", "CONNECTOR", 0L, requestKey);
        if (lease.shouldRun()) executor.connectorImport(lease.id(), projectId, type, scope, user);
        return lease.id();
    }
}
