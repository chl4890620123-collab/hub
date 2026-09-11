package com.hub.service;

import com.hub.dto.AiDtos;
import com.hub.model.SearchHit;
import com.hub.model.User;
import com.hub.repository.ChangeRepository;
import com.hub.repository.DocumentRepository;
import com.hub.repository.EvidenceRepository;
import com.hub.repository.TimelineRepository;
import com.hub.util.Hashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChangeService {
    private final DocumentRepository documents;
    private final AiClient ai;
    private final ChangeRepository changes;
    private final EvidenceRepository evidence;
    private final TimelineRepository timeline;
    private final TransactionTemplate transaction;

    public ChangeService(DocumentRepository documents,
                         AiClient ai,
                         ChangeRepository changes,
                         EvidenceRepository evidence,
                         TimelineRepository timeline,
                         org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.documents = documents;
        this.ai = ai;
        this.changes = changes;
        this.evidence = evidence;
        this.timeline = timeline;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * The LLM only classifies a text diff. Every non-empty before/after value must still be found
     * in the corresponding immutable document version before the change is persisted.
     */
    public AiDtos.ChangeResponse compare(long projectId, long beforeVersion, long afterVersion, User actor) {
        String before = documents.versionText(beforeVersion);
        String after = documents.versionText(afterVersion);
        AiDtos.ChangeResponse raw = ai.changes(before, after);
        List<SearchHit> beforeChunks = documents.chunksForVersion(beforeVersion);
        List<SearchHit> afterChunks = documents.chunksForVersion(afterVersion);
        List<GroundedChange> grounded = new ArrayList<>();

        if (raw != null && raw.changes() != null) {
            for (AiDtos.ChangeItem item : raw.changes()) {
                String beforeText = clean(item.before());
                String afterText = clean(item.after());
                SearchHit beforeHit = beforeText.isBlank() ? null : findChunk(beforeChunks, beforeText);
                SearchHit afterHit = afterText.isBlank() ? null : findChunk(afterChunks, afterText);
                if ((!beforeText.isBlank() && beforeHit == null) || (!afterText.isBlank() && afterHit == null)) {
                    continue;
                }
                grounded.add(new GroundedChange(item, beforeText, afterText, beforeHit, afterHit));
            }
        }

        transaction.executeWithoutResult(status -> {
            long analysisId = changes.createAnalysis(projectId, beforeVersion, afterVersion, actor.id());
            for (GroundedChange groundedChange : grounded) {
                AiDtos.ChangeItem item = groundedChange.item();
                long itemId = changes.addItem(
                        analysisId,
                        item.category(),
                        groundedChange.beforeText(),
                        groundedChange.afterText(),
                        item.reason()
                );
                if (groundedChange.beforeHit() != null) {
                    long evidenceId = evidence.createDocumentEvidence(
                            beforeVersion,
                            groundedChange.beforeHit().chunkId(),
                            groundedChange.beforeText(),
                            Hashing.sha256(groundedChange.beforeText())
                    );
                    evidence.linkChange(itemId, evidenceId, "BEFORE");
                }
                if (groundedChange.afterHit() != null) {
                    long evidenceId = evidence.createDocumentEvidence(
                            afterVersion,
                            groundedChange.afterHit().chunkId(),
                            groundedChange.afterText(),
                            Hashing.sha256(groundedChange.afterText())
                    );
                    evidence.linkChange(itemId, evidenceId, "AFTER");
                }
            }
            timeline.append(
                    projectId,
                    "DOCUMENT_CHANGED",
                    "Document change analysis",
                    "Grounded changes: " + grounded.size(),
                    LocalDateTime.now(),
                    "CHANGE_ANALYSIS",
                    analysisId
            );
        });

        return new AiDtos.ChangeResponse(grounded.stream().map(GroundedChange::item).toList());
    }

    public List<Map<String, Object>> list(long projectId) {
        return changes.list(projectId);
    }

    private static SearchHit findChunk(List<SearchHit> chunks, String text) {
        return chunks.stream().filter(chunk -> chunk.content().contains(text)).findFirst().orElse(null);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record GroundedChange(
            AiDtos.ChangeItem item,
            String beforeText,
            String afterText,
            SearchHit beforeHit,
            SearchHit afterHit
    ) {
    }
}
