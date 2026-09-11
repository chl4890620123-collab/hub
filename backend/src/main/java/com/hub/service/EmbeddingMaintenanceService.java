package com.hub.service;

import com.hub.dto.AiDtos;
import com.hub.model.SearchHit;
import com.hub.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps semantic search recoverable after temporary AI/embedding outages. */
@Service
public class EmbeddingMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingMaintenanceService.class);
    private final DocumentRepository documents;
    private final AiClient ai;
    private final VectorIndexService vectors;
    private final boolean enabled;

    public EmbeddingMaintenanceService(DocumentRepository documents,
                                       AiClient ai,
                                       VectorIndexService vectors,
                                       @Value("${hub.embedding-retry-enabled:true}") boolean enabled) {
        this.documents = documents;
        this.ai = ai;
        this.vectors = vectors;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${hub.embedding-retry-interval-ms:600000}",
            initialDelayString = "${hub.embedding-retry-initial-delay-ms:60000}")
    public void retryBackground() {
        if (!enabled) return;
        for (DocumentRepository.EmbeddingRetryCandidate candidate : documents.embeddingRetryCandidates(10)) {
            retryVersion(candidate.versionId());
        }
    }

    public int retryProject(long projectId, int limit) {
        int ready = 0;
        for (DocumentRepository.EmbeddingRetryCandidate candidate : documents.embeddingRetryCandidates(projectId, limit)) {
            if (retryVersion(candidate.versionId())) ready++;
        }
        return ready;
    }

    public Map<String,Object> health(long projectId) {
        return new LinkedHashMap<>(documents.embeddingHealth(projectId));
    }

    private boolean retryVersion(long versionId) {
        List<SearchHit> chunks = documents.chunksForVersion(versionId);
        if (chunks.isEmpty()) {
            documents.markEmbeddingStatus(versionId, "FAILED", "No document chunks", true);
            return false;
        }
        try {
            final int batchSize = 64;
            List<List<Float>> all = new ArrayList<>(chunks.size());
            for (int start = 0; start < chunks.size(); start += batchSize) {
                int end = Math.min(start + batchSize, chunks.size());
                List<String> batch = chunks.subList(start, end).stream().map(SearchHit::content).toList();
                AiDtos.EmbedResponse embedded = ai.embed(batch, "passage");
                if (embedded == null || embedded.dimensions() != 768 || embedded.vectors() == null
                        || embedded.vectors().size() != batch.size()) {
                    throw new IllegalStateException("Embedding response shape does not match vector(768)");
                }
                all.addAll(embedded.vectors());
            }
            for (int i = 0; i < chunks.size(); i++) vectors.store(chunks.get(i).chunkId(), all.get(i));
            documents.markEmbeddingStatus(versionId, "READY", null, true);
            return true;
        } catch (RuntimeException failure) {
            documents.markEmbeddingStatus(versionId, "FAILED", failure.getMessage(), true);
            log.warn("Embedding retry failed for version {}: {}", versionId, failure.getMessage());
            return false;
        }
    }
}
