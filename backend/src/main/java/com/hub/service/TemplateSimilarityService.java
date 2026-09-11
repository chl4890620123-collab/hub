package com.hub.service;

import com.hub.dto.AiDtos;
import com.hub.model.SearchHit;
import com.hub.repository.ConnectorRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds documents that resemble a configured reference/original form even when their filenames differ.
 * The reference file is used as a semantic template, not as a hard-coded search result.
 */
@Service
public class TemplateSimilarityService {
    private static final int MAX_TEMPLATE_PARTS = 3;
    private static final int MAX_TEMPLATE_PART_CHARS = 1800;
    private static final int MAX_NEAREST_PER_PART = 80;
    private static final int STRUCTURAL_CANDIDATES = 30;
    private static final int RRF_K = 60;

    private final DocumentContextService documents;
    private final ConnectorRepository connectors;
    private final AiClient ai;
    private final VectorIndexService vectors;
    private final Map<String, List<List<Float>>> templateVectorCache = new ConcurrentHashMap<>();

    public TemplateSimilarityService(DocumentContextService documents,
                                     ConnectorRepository connectors,
                                     AiClient ai,
                                     VectorIndexService vectors) {
        this.documents = documents;
        this.connectors = connectors;
        this.ai = ai;
        this.vectors = vectors;
    }

    public List<TemplateMatch> similar(long projectId, String referenceFilename, int limit) {
        String filename = referenceFilename == null ? "" : referenceFilename.trim();
        if (filename.isBlank()) return List.of();

        Reference reference = reference(projectId, filename);
        if (reference == null || reference.parts().isEmpty()) return List.of();

        List<List<Float>> templateVectors;
        try {
            templateVectors = templateVectorCache.computeIfAbsent(reference.cacheKey(), ignored -> embed(reference.parts()));
        } catch (RuntimeException ignored) {
            return List.of();
        }
        if (templateVectors == null || templateVectors.isEmpty()) return List.of();

        Map<Long, ScoredHit> byDocument = new LinkedHashMap<>();
        for (List<Float> vector : templateVectors) {
            if (vector == null || vector.isEmpty()) continue;
            int rank = 1;
            for (SearchHit hit : vectors.nearest(projectId, vector, MAX_NEAREST_PER_PART)) {
                if (reference.matches(hit)) {
                    rank++;
                    continue;
                }
                double score = 1.0d / (RRF_K + rank++) + structureBoost(reference, hit);
                ScoredHit current = byDocument.get(hit.documentId());
                if (current == null) {
                    byDocument.put(hit.documentId(), new ScoredHit(hit, score));
                } else {
                    current.add(score);
                }
            }
        }

        // Content can be completely different while the office form is the same. Add a bounded
        // same-extension/section-shape pass so template search is not semantic-only.
        if (reference.documentId() != null && !reference.extension().isBlank()) {
            for (var ref : documents.matchingLatestDocuments(projectId, List.of("*." + reference.extension()), STRUCTURAL_CANDIDATES)) {
                if (ref.documentId() == reference.documentId()) continue;
                List<SearchHit> chunks = documents.chunks(ref.versionId());
                SearchHit best = chunks.stream()
                        .max(Comparator.comparingDouble(hit -> structureBoost(reference, hit)))
                        .orElse(null);
                if (best == null) continue;
                double score = 0.012d + structureBoost(reference, best);
                ScoredHit current = byDocument.get(best.documentId());
                if (current == null) byDocument.put(best.documentId(), new ScoredHit(best, score));
                else current.add(score);
            }
        }

        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<ScoredHit> sorted = byDocument.values().stream()
                .sorted(Comparator.comparingDouble(ScoredHit::score).reversed())
                .limit(safeLimit)
                .toList();
        List<TemplateMatch> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            out.add(new TemplateMatch(sorted.get(i).hit(), i + 1));
        }
        return List.copyOf(out);
    }

    private Reference reference(long projectId, String filename) {
        var nativeRefs = documents.matchingExactDocument(projectId, filename);
        if (!nativeRefs.isEmpty()) {
            var ref = nativeRefs.get(0);
            List<SearchHit> chunks = documents.chunks(ref.versionId());
            List<String> parts = representativeParts(chunks.stream().map(SearchHit::content).toList());
            return new Reference("HUB:" + ref.versionId(), ref.documentId(), "", parts, extension(filename),
                    paragraphShapes(chunks), dominantContentShape(chunks.stream().map(SearchHit::content).toList()));
        }

        var external = connectors.searchByExactTitle(projectId, filename, 1);
        if (!external.isEmpty()) {
            var row = external.get(0);
            String content = row.content() == null || row.content().isBlank() ? row.title() : row.content();
            List<String> parts = representativeParts(List.of(content));
            String hash = Integer.toUnsignedString(content == null ? 0 : content.hashCode());
            return new Reference("EXT:" + row.id() + ":" + hash, null, row.externalId(), parts,
                    extension(row.title()), Set.of(), dominantContentShape(List.of(content)));
        }
        return null;
    }

    private List<List<Float>> embed(List<String> parts) {
        AiDtos.EmbedResponse response = ai.embed(parts, "query");
        if (response == null || response.vectors() == null) return List.of();
        return List.copyOf(response.vectors());
    }

    static List<String> representativeParts(List<String> source) {
        List<String> nonBlank = source == null ? List.of() : source.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .toList();
        if (nonBlank.isEmpty()) return List.of();

        List<Integer> indexes = new ArrayList<>();
        indexes.add(0);
        if (nonBlank.size() > 2) indexes.add(nonBlank.size() / 2);
        if (nonBlank.size() > 1) indexes.add(nonBlank.size() - 1);

        List<String> out = new ArrayList<>();
        for (Integer index : indexes) {
            String text = nonBlank.get(index).replaceAll("\\s+", " ").trim();
            if (text.length() > MAX_TEMPLATE_PART_CHARS) text = text.substring(0, MAX_TEMPLATE_PART_CHARS);
            if (!out.contains(text)) out.add(text);
            if (out.size() >= MAX_TEMPLATE_PARTS) break;
        }
        return List.copyOf(out);
    }

    private static double structureBoost(Reference reference, SearchHit hit) {
        double score = 0.0d;
        String extension = extension(hit.documentName());
        if (!reference.extension().isBlank() && reference.extension().equals(extension)) score += 0.016d;
        String paragraph = paragraphShape(hit.paragraphRef());
        if (!paragraph.isBlank() && reference.paragraphShapes().contains(paragraph)) score += 0.020d;
        String shape = contentShape(hit.content());
        if (!reference.contentShape().isBlank() && reference.contentShape().equals(shape)) score += 0.014d;
        return score;
    }

    private static Set<String> paragraphShapes(List<SearchHit> chunks) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (SearchHit hit : chunks) {
            String shape = paragraphShape(hit.paragraphRef());
            if (!shape.isBlank()) out.add(shape);
            if (out.size() >= 12) break;
        }
        return Set.copyOf(out);
    }

    private static String paragraphShape(String value) {
        if (value == null || value.isBlank()) return "";
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\d+", "#")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String dominantContentShape(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String value : values) counts.merge(contentShape(value), 1, Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private static String contentShape(String value) {
        String text = value == null ? "" : value;
        if (text.isBlank()) return "";
        long pipes = text.chars().filter(c -> c == '|').count();
        long tabs = text.chars().filter(c -> c == '\t').count();
        long colons = text.chars().filter(c -> c == ':' || c == '：').count();
        long newlines = text.chars().filter(c -> c == '\n').count();
        if (pipes >= 3 || tabs >= 3) return "TABLE";
        if (colons >= 3 && newlines >= 2) return "FORM";
        if (text.matches("(?s).*(?:^|\\n)\\s*(?:[-*•]|\\d+[.)])\\s+.*")) return "LIST";
        return "PROSE";
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        String value = filename.trim();
        int dot = value.lastIndexOf('.');
        if (dot < 0 || dot == value.length() - 1) return "";
        String extension = value.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return extension.matches("[a-z0-9]{1,8}") ? extension : "";
    }

    private record Reference(String cacheKey, Long documentId, String sourceIdentifier, List<String> parts,
                             String extension, Set<String> paragraphShapes, String contentShape) {
        boolean matches(SearchHit hit) {
            if (documentId != null && hit.documentId() == documentId) return true;
            return sourceIdentifier != null && !sourceIdentifier.isBlank()
                    && sourceIdentifier.equalsIgnoreCase(hit.sourceIdentifier());
        }
    }

    private static final class ScoredHit {
        private final SearchHit hit;
        private double score;

        private ScoredHit(SearchHit hit, double score) {
            this.hit = hit;
            this.score = score;
        }

        private void add(double value) { this.score += value; }
        private SearchHit hit() { return hit; }
        private double score() { return score; }
    }

    public record TemplateMatch(SearchHit hit, int rank) {}
}
