package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.model.DocumentVersionRef;
import com.hub.model.SearchHit;
import com.hub.repository.DocumentRepository;
import com.hub.util.UnicodeText;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts chunk-first retrieval into document-first RAG context.
 * Small documents are read in full; large documents expand around the best matching chunks.
 * Every context piece keeps the real chunk id so Evidence remains exact.
 */
@Service
public class DocumentContextService {
    private final DocumentRepository documents;
    private final HubProperties props;

    public DocumentContextService(DocumentRepository documents, HubProperties props) {
        this.documents = documents;
        this.props = props;
    }

    public List<DocumentVersionRef> matchingLatestDocuments(long projectId, List<String> patterns, int limit) {
        return documents.latestByNamePatterns(projectId, patterns, limit);
    }

    public List<DocumentVersionRef> matchingExactDocument(long projectId, String filename) {
        return documents.latestByExactName(projectId, filename, 1);
    }

    public List<SearchHit> chunks(long versionId) {
        return documents.chunksForVersion(versionId);
    }

    public List<ContextItem> build(long projectId,
                                   String query,
                                   List<SearchHit> rankedSeeds,
                                   SearchRuleService.RuleMatch ruleMatch) {
        LinkedHashMap<Long, DocumentSelection> selected = new LinkedHashMap<>();

        if (ruleMatch != null) {
            // targetFile is normally a reference/original form. It guides template similarity, but the
            // blank reference itself should not become RAG context unless the user explicitly names it.
            if (ruleMatch.targetFile() != null && !ruleMatch.targetFile().isBlank()
                    && !UnicodeText.nfc(query).isBlank()
                    && UnicodeText.nfc(query).toLowerCase(Locale.ROOT)
                    .contains(UnicodeText.nfc(ruleMatch.targetFile()).toLowerCase(Locale.ROOT))) {
                for (DocumentVersionRef ref : documents.latestByExactName(projectId, ruleMatch.targetFile(), 1)) {
                    selected.putIfAbsent(ref.documentId(), new DocumentSelection(ref, new ArrayList<>(), ruleMatch.mode(), true));
                }
            }
            for (DocumentVersionRef ref : documents.latestByNamePatterns(
                    projectId, ruleMatch.patterns(), props.searchMaxDocuments())) {
                selected.putIfAbsent(ref.documentId(), new DocumentSelection(ref, new ArrayList<>(), ruleMatch.mode(), true));
            }
        }

        for (SearchHit seed : rankedSeeds) {
            if (selected.size() >= props.searchMaxDocuments()) break;
            DocumentSelection selection = selected.computeIfAbsent(seed.documentId(), ignored ->
                    new DocumentSelection(
                            new DocumentVersionRef(seed.documentId(), seed.versionId(), seed.versionNo(),
                                    seed.documentName(), seed.sourceType(), seed.sourceIdentifier(), ""),
                            new ArrayList<>(), "smart", false));
            selection.seeds().add(seed);
        }

        List<ContextItem> out = new ArrayList<>();
        int usedChars = 0;
        for (DocumentSelection selection : selected.values()) {
            if (out.size() >= props.ragMaxChunks() || usedChars >= props.ragMaxContextChars()) break;
            List<SearchHit> chunks = documents.chunksForVersion(selection.ref().versionId());
            if (chunks.isEmpty()) continue;
            String fullText = selection.ref().fullText();
            if (fullText == null || fullText.isBlank()) fullText = documents.versionText(selection.ref().versionId());

            List<SearchHit> chosen;
            boolean whole = fullText != null && fullText.length() <= props.searchWholeDocumentMaxChars();
            if ("full".equalsIgnoreCase(selection.mode()) && fullText != null
                    && fullText.length() <= props.ragMaxContextChars()) {
                whole = true;
            }
            if (whole) {
                chosen = chunks;
            } else {
                chosen = neighborhood(chunks, selection.seeds(), query, props.searchNeighborChunks());
            }

            for (SearchHit chunk : chosen) {
                if (out.size() >= props.ragMaxChunks()) break;
                int remaining = props.ragMaxContextChars() - usedChars;
                if (remaining <= 0) break;
                String text = chunk.content() == null ? "" : chunk.content().trim();
                if (text.isBlank()) continue;
                if (text.length() > remaining) text = text.substring(0, remaining);
                out.add(new ContextItem(chunk, text,
                        joinLocation(chunk.documentName(), chunk.paragraphRef()), whole, selection.pinned()));
                usedChars += text.length() + 2;
            }
        }
        return List.copyOf(out);
    }

    private static List<SearchHit> neighborhood(List<SearchHit> chunks,
                                                List<SearchHit> seeds,
                                                String query,
                                                int neighbors) {
        Map<Long, Integer> indexById = new HashMap<>();
        for (int i = 0; i < chunks.size(); i++) indexById.put(chunks.get(i).chunkId(), i);

        LinkedHashSet<Integer> seedIndexes = new LinkedHashSet<>();
        for (SearchHit seed : seeds) {
            Integer index = indexById.get(seed.chunkId());
            if (index != null) seedIndexes.add(index);
            if (seedIndexes.size() >= 2) break;
        }
        if (seedIndexes.isEmpty()) {
            chunks.stream()
                    .map(chunk -> new RankedChunk(chunk, lexicalScore(chunk, query)))
                    .sorted(Comparator.comparingInt(RankedChunk::score).reversed())
                    .limit(2)
                    .forEach(item -> seedIndexes.add(indexById.get(item.chunk().chunkId())));
        }
        if (seedIndexes.isEmpty()) seedIndexes.add(0);

        Set<Integer> chosen = new LinkedHashSet<>();
        int radius = Math.max(0, Math.min(neighbors, 5));
        for (Integer center : seedIndexes) {
            for (int i = Math.max(0, center - radius); i <= Math.min(chunks.size() - 1, center + radius); i++) {
                chosen.add(i);
            }
        }
        return chosen.stream().sorted().map(chunks::get).toList();
    }

    private static int lexicalScore(SearchHit hit, String query) {
        String haystack = UnicodeText.nfc((hit.documentName() == null ? "" : hit.documentName()) + " "
                + (hit.paragraphRef() == null ? "" : hit.paragraphRef()) + " "
                + (hit.content() == null ? "" : hit.content())).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens(query)) {
            if (haystack.contains(token)) score += token.length() + 2;
        }
        return score;
    }

    private static List<String> tokens(String query) {
        if (query == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_./#-]+")) {
            if (token.length() >= 2) out.add(token);
        }
        return out;
    }

    private static String joinLocation(String document, String paragraph) {
        if (paragraph == null || paragraph.isBlank()) return document == null ? "Hub 문서" : document;
        return (document == null || document.isBlank() ? "Hub 문서" : document) + " / " + paragraph;
    }

    private record DocumentSelection(DocumentVersionRef ref, List<SearchHit> seeds, String mode, boolean pinned) {}
    private record RankedChunk(SearchHit chunk, int score) {}

    public record ContextItem(SearchHit hit, String text, String location, boolean wholeDocument, boolean pinned) {}
}
