package com.hub.service;

import com.hub.repository.ConnectorRepository;
import com.hub.repository.DocumentRepository;
import com.hub.repository.MeetingRepository;
import com.hub.repository.RefreshTokenRepository;
import com.hub.repository.SearchLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Keeps a few log-shaped tables from growing forever, the same way {@link TodoRetentionService} bounds
 * the todo table: nothing here is live application state, so removing old rows is safe.
 * - refresh_token: rows already past their own expires_at are otherwise only swept on the next login
 *   anywhere in the system, so an idle deployment can carry them indefinitely.
 * - search_log: a plain query log with no other table referencing it.
 * - external_item: import bookkeeping left behind after a connector is disconnected
 *   (connector_account_id is SET NULL on disconnect); the imported document itself is never touched.
 * - archived documents past retention: only their full_text/content/embedding columns are cleared: the
 *   document/version/chunk rows stay so evidence/decision/todo/change records that cite them by id keep
 *   working. Search/RAG already exclude archived documents, so nothing user-visible changes but size.
 * - meeting STT transcripts past their own (shorter) retention: same clear-content-keep-the-row shape.
 *   No controller ever exposes the raw transcript/segment text to a client - it is only an AI analysis
 *   input and the evidence-grounding source at creation time, and every evidence quote already has its
 *   own independent copy in evidence.evidence_text - so "근거 보기" keeps working after this runs.
 */
@Service
public class DataRetentionService {
    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final RefreshTokenRepository refreshTokens;
    private final SearchLogRepository searchLogs;
    private final ConnectorRepository connectors;
    private final DocumentRepository documents;
    private final MeetingRepository meetings;
    private final boolean enabled;
    private final int retentionMonths;
    private final int sttRetentionMonths;

    public DataRetentionService(RefreshTokenRepository refreshTokens,
                                SearchLogRepository searchLogs,
                                ConnectorRepository connectors,
                                DocumentRepository documents,
                                MeetingRepository meetings,
                                @Value("${hub.data-retention-enabled:true}") boolean enabled,
                                @Value("${hub.data-retention-months:12}") int retentionMonths,
                                @Value("${hub.stt-retention-months:6}") int sttRetentionMonths) {
        this.refreshTokens = refreshTokens;
        this.searchLogs = searchLogs;
        this.connectors = connectors;
        this.documents = documents;
        this.meetings = meetings;
        this.enabled = enabled;
        this.retentionMonths = retentionMonths;
        this.sttRetentionMonths = sttRetentionMonths;
    }

    @Scheduled(fixedDelayString = "${hub.data-retention-interval-ms:86400000}",
            initialDelayString = "${hub.data-retention-initial-delay-ms:600000}")
    public void purgeOldData() {
        if (!enabled) return;
        refreshTokens.deleteExpired();

        LocalDate cutoff = LocalDate.now().minusMonths(Math.max(1, retentionMonths));
        int searchLogsRemoved = searchLogs.purgeOlderThan(cutoff);
        if (searchLogsRemoved > 0) log.info("Data retention cleanup removed {} search log row(s) before {}", searchLogsRemoved, cutoff);

        int orphanedItemsRemoved = connectors.purgeOrphanedItemsOlderThan(cutoff);
        if (orphanedItemsRemoved > 0) log.info("Data retention cleanup removed {} orphaned external_item row(s) before {}", orphanedItemsRemoved, cutoff);

        int archivedVersionsCleared = documents.purgeArchivedContentOlderThan(cutoff);
        if (archivedVersionsCleared > 0) log.info("Data retention cleanup cleared content for {} archived document version(s) before {}", archivedVersionsCleared, cutoff);

        LocalDate sttCutoff = LocalDate.now().minusMonths(Math.max(1, sttRetentionMonths));
        int meetingsCleared = meetings.purgeTranscriptsOlderThan(sttCutoff);
        if (meetingsCleared > 0) log.info("Data retention cleanup cleared STT transcript text for {} meeting(s) before {}", meetingsCleared, sttCutoff);
    }
}
