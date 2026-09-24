package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.dto.AiDtos;
import com.hub.model.MaterialAskResponse;
import com.hub.model.MaterialHit;
import com.hub.model.SearchHit;
import com.hub.repository.ConnectorRepository;
import com.hub.repository.DocumentRepository;
import com.hub.repository.FileAttachmentRepository;
import com.hub.util.SearchText;
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

@Service
public class MaterialSearchService {
    private static final int MAX_RESULTS = 30;
    private static final int SEMANTIC_CANDIDATES = 60;
    private static final int LEXICAL_CANDIDATES = 100;
    private static final int TEMPLATE_CANDIDATES = 40;
    private static final int RRF_K = 60;

    private final ConnectorRepository connectors;
    private final DocumentRepository documents;
    private final FileAttachmentRepository attachments;
    private final LexicalSearchService lexicalSearch;
    private final AiClient ai;
    private final VectorIndexService vectors;
    private final ObjectMapper json;
    private final SearchRuleService searchRules;
    private final DocumentContextService documentContext;
    private final TemplateSimilarityService templateSimilarity;
    private final SearchQueryRouter queryRouter;
    private final HubProperties props;

    public MaterialSearchService(ConnectorRepository connectors,
                                 DocumentRepository documents,
                                 FileAttachmentRepository attachments,
                                 LexicalSearchService lexicalSearch,
                                 AiClient ai,
                                 VectorIndexService vectors,
                                 ObjectMapper json,
                                 SearchRuleService searchRules,
                                 DocumentContextService documentContext,
                                 TemplateSimilarityService templateSimilarity,
                                 SearchQueryRouter queryRouter,
                                 HubProperties props) {
        this.connectors = connectors;
        this.documents = documents;
        this.attachments = attachments;
        this.lexicalSearch = lexicalSearch;
        this.ai = ai;
        this.vectors = vectors;
        this.json = json;
        this.searchRules = searchRules;
        this.documentContext = documentContext;
        this.templateSimilarity = templateSimilarity;
        this.queryRouter = queryRouter;
        this.props = props;
    }

    /**
     * User-visible search keeps the existing flow while combining ANN meaning, filename/title and
     * keyword retrieval. Historical versions remain auditable but are excluded from default search.
     */
    public List<MaterialHit> search(long projectId, String query) {
        return search(projectId, query, 0);
    }

    /**
     * offset pages through the same fully-ranked candidate list rankCandidates already builds
     * (bounded by the per-engine candidate caps above, not by MAX_RESULTS) - "load more" is a
     * second slice of that list, not a second query with a bigger limit.
     */
    public List<MaterialHit> search(long projectId, String query, int offset) {
        String normalized = requiredQuery(query);
        SearchQueryPlan plan = queryRouter.route(normalized);
        SearchRuleService.RuleMatch rule = searchRules.match(projectId, normalized).orElse(null);
        List<MaterialHit> primary = recommendDocuments(rankCandidates(projectId, plan, rule), plan, Math.max(0, offset));
        if (offset > 0 || primary.size() >= MAX_RESULTS) return primary;
        // Attachment metadata is deliberately additive: failures here must not break the proven document search path.
        try {
            List<MaterialHit> merged = new ArrayList<>(primary);
            Set<String> seen = new LinkedHashSet<>();
            primary.forEach(hit -> seen.add(hit.sourceType() + ":" + hit.evidenceId()));
            int rank = merged.size() + 1;
            for (FileAttachmentRepository.Attachment attachment : attachments.search(projectId, plan.searchText(), MAX_RESULTS)) {
                String key = "ATTACHMENT:" + attachment.id();
                if (!seen.add(key)) continue;
                String note = blankTo(attachment.note(), "첨부파일 이름이 검색어와 일치합니다.");
                merged.add(new MaterialHit(
                        attachment.id(), "ATTACHMENT", "업무 첨부파일", "ATTACHMENT", attachment.fileName(),
                        attachment.todoId() == null ? "프로젝트 파일 전송" : "할 일 #" + attachment.todoId() + " 첨부파일",
                        excerpt(note, normalized, SearchText.terms(plan.searchText()), 620), "", "",
                        attachment.createdAt().atOffset(java.time.ZoneOffset.UTC), rank++, "첨부파일 일치",
                        "현재 프로젝트의 첨부파일 이름 또는 메모에서 찾았습니다."
                ));
                if (merged.size() >= MAX_RESULTS) break;
            }
            return merged;
        } catch (RuntimeException ignored) {
            return primary;
        }
    }

    /**
     * Document-first RAG: retrieval first decides which documents are relevant. Small latest versions
     * are then read as a whole; large versions expand around the best matching chunks. Configured
     * aliases such as "기획서" can activate an original-form reference without hard-coding filenames in Java.
     */
    public MaterialAskResponse ask(long projectId, String question) {
        String normalized = requiredQuery(question);
        SearchQueryPlan plan = queryRouter.route(normalized);
        List<String> terms = SearchText.terms(plan.searchText());
        SearchRuleService.RuleMatch rule = searchRules.match(projectId, normalized).orElse(null);
        List<Candidate> ranked = rankCandidates(projectId, plan, rule);

        List<SearchHit> hubSeeds = ranked.stream()
                .map(Candidate::nativeSeed)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<DocumentContextService.ContextItem> hubContext =
                documentContext.build(projectId, plan.searchText(), hubSeeds, rule);

        List<AiDtos.RagChunk> chunks = new ArrayList<>();
        Map<Long, MaterialHit> sourceByEvidence = new LinkedHashMap<>();
        int usedChars = 0;
        int displayRank = 1;

        for (DocumentContextService.ContextItem item : hubContext) {
            if (chunks.size() >= props.ragMaxChunks()) break;
            int remaining = props.ragMaxContextChars() - usedChars;
            if (remaining <= 0) break;
            String text = item.text().length() > remaining ? item.text().substring(0, remaining) : item.text();
            chunks.add(new AiDtos.RagChunk(item.hit().chunkId(), text, item.location()));
            MaterialHit base = nativeHit(item.hit(), normalized, terms);
            String type = item.pinned() ? "설정 문서" : item.wholeDocument() ? "문서 전체 맥락" : "관련 문맥";
            String reason = item.pinned()
                    ? "설정된 파일 별칭과 제목 규칙으로 문서를 먼저 선택한 뒤 최신 내용을 읽었습니다."
                    : item.wholeDocument()
                    ? "관련 문서를 먼저 선택한 뒤 최신 버전 전체를 읽어 답변 근거로 사용했습니다."
                    : "관련 문서를 먼저 선택하고 일치 구간의 앞뒤 문맥까지 확장해 읽었습니다.";
            sourceByEvidence.put(item.hit().chunkId(), recommend(base, displayRank++, type, reason));
            usedChars += text.length() + 2;
        }

        // Connector snapshots are already document-level records. Use more than the short UI snippet,
        // while still respecting the same bounded context window used by the AI service.
        Set<Long> usedExternal = new LinkedHashSet<>();
        for (Candidate candidate : ranked) {
            ConnectorRepository.ExternalSearchRow row = candidate.externalRow();
            if (row == null || !usedExternal.add(row.id())) continue;
            if (chunks.size() >= props.ragMaxChunks() || usedChars >= props.ragMaxContextChars()) break;
            int remaining = props.ragMaxContextChars() - usedChars;
            int perSource = Math.min(6000, remaining);
            String text = contextExcerpt(blankTo(row.content(), row.title()), normalized, terms, perSource);
            if (text.isBlank()) continue;
            long evidenceId = -row.id();
            chunks.add(new AiDtos.RagChunk(evidenceId, text, externalLocation(row)));
            MaterialHit base = externalHit(row, row.content(), normalized, terms);
            sourceByEvidence.put(evidenceId, recommend(base, displayRank++, "외부 문서 맥락",
                    "연결 서비스에서 관련 자료를 먼저 선택하고 가능한 범위의 본문 맥락을 읽었습니다."));
            usedChars += text.length() + 2;
        }

        if (chunks.isEmpty()) {
            return new MaterialAskResponse("관련 자료에서 해당 내용을 확인하지 못했습니다.", List.of());
        }

        AiDtos.RagResponse answer = ai.rag(normalized, chunks);
        Set<Long> evidenceIds = new LinkedHashSet<>();
        if (answer.evidence() != null) answer.evidence().forEach(e -> evidenceIds.add(e.id()));
        List<MaterialHit> used = evidenceIds.stream()
                .map(sourceByEvidence::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (used.isEmpty()) {
            return new MaterialAskResponse("관련 자료에서 해당 내용을 확인하지 못했습니다.", List.of());
        }
        return new MaterialAskResponse(answer.answer(), used);
    }

    private List<Candidate> rankCandidates(long projectId, SearchQueryPlan plan, SearchRuleService.RuleMatch rule) {
        String query = plan.searchText();
        List<String> terms = SearchText.terms(query);
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        // Query routing changes channel priority rather than replacing the proven hybrid engines.
        addRuleCandidates(projectId, plan.originalQuery(), terms, rule, candidates);
        addTemplateSimilarityCandidates(projectId, query, terms, rule, candidates);
        addMetadataCandidates(projectId, query, terms, plan, candidates);
        addNativeLexicalCandidates(projectId, query, terms, candidates);
        addExternalLexicalCandidates(projectId, query, terms, candidates);
        if (plan.intent() != SearchQueryPlan.Intent.EXACT_FILE || candidates.isEmpty()) {
            addSemanticCandidates(projectId, query, terms, candidates);
        }
        candidates.values().forEach(candidate -> candidate.rerankBonus = rerankBonus(candidate.hit(), plan, terms));
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.score(plan)).reversed()
                        .thenComparing(c -> c.hit().sourceCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void addRuleCandidates(long projectId,
                                   String query,
                                   List<String> terms,
                                   SearchRuleService.RuleMatch rule,
                                   Map<String, Candidate> out) {
        if (rule == null) return;
        int rank = 1;
        // targetFile is the reference/original form. Do not force the blank template into every result.
        // Only return the reference itself when the user explicitly typed that exact filename.
        if (rule.targetFile() != null && !rule.targetFile().isBlank()
                && SearchText.lower(query).contains(SearchText.lower(rule.targetFile()))) {
            for (var ref : documentContext.matchingExactDocument(projectId, rule.targetFile())) {
                List<SearchHit> chunks = documentContext.chunks(ref.versionId());
                SearchHit best = bestRuleChunk(chunks, query, terms);
                if (best == null) continue;
                MaterialHit material = nativeHit(best, query, terms);
                Candidate current = out.computeIfAbsent("HUB:" + best.chunkId(), ignored -> new Candidate(material, "HUB-DOC:" + best.documentId()));
                current.attach(best, null);
                current.ruleRank = Math.min(current.ruleRank, rank++);
                current.ruleName = rule.name();
            }
            for (ConnectorRepository.ExternalSearchRow row : connectors.searchByExactTitle(projectId, rule.targetFile(), 1)) {
                MaterialHit material = externalHit(row, row.content(), query, terms);
                Candidate current = out.computeIfAbsent("EXT:" + row.id(), ignored -> new Candidate(material, "EXT:" + row.id()));
                current.attach(null, row);
                current.ruleRank = Math.min(current.ruleRank, rank++);
                current.ruleName = rule.name();
            }
        }
        if (rule.patterns() == null || rule.patterns().isEmpty()) return;
        for (var ref : documentContext.matchingLatestDocuments(projectId, rule.patterns(), props.searchMaxDocuments())) {
            List<SearchHit> chunks = documentContext.chunks(ref.versionId());
            SearchHit best = bestRuleChunk(chunks, query, terms);
            if (best == null) continue;
            MaterialHit material = nativeHit(best, query, terms);
            Candidate current = out.computeIfAbsent("HUB:" + best.chunkId(), ignored -> new Candidate(material, "HUB-DOC:" + best.documentId()));
            current.attach(best, null);
            current.ruleRank = Math.min(current.ruleRank, rank++);
            current.ruleName = rule.name();
        }
        for (ConnectorRepository.ExternalSearchRow row : connectors.searchByTitlePatterns(projectId, rule.patterns(), props.searchMaxDocuments())) {
            MaterialHit material = externalHit(row, row.content(), query, terms);
            Candidate current = out.computeIfAbsent("EXT:" + row.id(), ignored -> new Candidate(material, "EXT:" + row.id()));
            current.attach(null, row);
            current.ruleRank = Math.min(current.ruleRank, rank++);
            current.ruleName = rule.name();
        }
    }

    private void addTemplateSimilarityCandidates(long projectId,
                                                 String query,
                                                 List<String> terms,
                                                 SearchRuleService.RuleMatch rule,
                                                 Map<String, Candidate> out) {
        if (rule == null || rule.targetFile() == null || rule.targetFile().isBlank()) return;
        try {
            for (TemplateSimilarityService.TemplateMatch match :
                    templateSimilarity.similar(projectId, rule.targetFile(), TEMPLATE_CANDIDATES)) {
                SearchHit hit = match.hit();
                if (isExternalSource(hit.sourceType())) {
                    connectors.findByExternalId(projectId, hit.sourceIdentifier()).ifPresent(row -> {
                        MaterialHit material = externalHit(row, hit.content(), query, terms);
                        Candidate current = out.computeIfAbsent("EXT:" + row.id(),
                                ignored -> new Candidate(material, "EXT:" + row.id()));
                        current.attach(null, row);
                        current.templateRank = Math.min(current.templateRank, match.rank());
                        current.templateName = rule.name();
                    });
                } else {
                    MaterialHit material = nativeHit(hit, query, terms);
                    Candidate current = out.computeIfAbsent("HUB:" + hit.chunkId(),
                            ignored -> new Candidate(material, "HUB-DOC:" + hit.documentId()));
                    current.attach(hit, null);
                    current.templateRank = Math.min(current.templateRank, match.rank());
                    current.templateName = rule.name();
                }
            }
        } catch (RuntimeException ignored) {
            // If semantic template matching is temporarily unavailable, filename/keyword/natural-language search stays usable.
        }
    }

    private static SearchHit bestRuleChunk(List<SearchHit> chunks, String query, List<String> terms) {
        if (chunks == null || chunks.isEmpty()) return null;
        return chunks.stream().max(Comparator.comparingDouble(hit -> {
            String text = SearchText.lower(hit.content());
            double score = text.contains(SearchText.lower(query)) ? 10.0d : 0.0d;
            for (String term : terms) if (text.contains(SearchText.lower(term))) score += 1.0d;
            return score;
        })).orElse(chunks.get(0));
    }

    private void addMetadataCandidates(long projectId,
                                       String query,
                                       List<String> terms,
                                       SearchQueryPlan plan,
                                       Map<String, Candidate> out) {
        if (!plan.hasMetadataFilters()) return;
        int rank = 1;
        for (SearchHit hit : documents.searchNativeByMetadata(projectId, plan.author(), plan.fromInclusive(),
                plan.toExclusive(), plan.sourceTypes(), LEXICAL_CANDIDATES)) {
            MaterialHit material = nativeHit(hit, query, terms);
            mergeMetadata(out, "HUB:" + hit.chunkId(), "HUB-DOC:" + hit.documentId(), material, hit, null, rank++);
        }
        for (ConnectorRepository.ExternalSearchRow row : connectors.searchByMetadata(projectId, plan.author(),
                plan.fromInclusive(), plan.toExclusive(), plan.sourceTypes(), LEXICAL_CANDIDATES)) {
            MaterialHit material = externalHit(row, row.content(), query, terms);
            mergeMetadata(out, "EXT:" + row.id(), "EXT:" + row.id(), material, null, row, rank++);
        }
    }

    private void mergeMetadata(Map<String, Candidate> out,
                               String key,
                               String sourceKey,
                               MaterialHit hit,
                               SearchHit nativeSeed,
                               ConnectorRepository.ExternalSearchRow externalRow,
                               int rank) {
        Candidate current = out.computeIfAbsent(key, ignored -> new Candidate(hit, sourceKey));
        current.attach(nativeSeed, externalRow);
        current.metadataRank = Math.min(current.metadataRank, rank);
    }

    private void addSemanticCandidates(long projectId,
                                       String query,
                                       List<String> terms,
                                       Map<String, Candidate> out) {
        try {
            AiDtos.EmbedResponse embedded = ai.embed(List.of(query), "query");
            if (embedded == null || embedded.vectors() == null || embedded.vectors().isEmpty()) return;
            int rank = 1;
            for (SearchHit hit : vectors.nearest(projectId, embedded.vectors().get(0), SEMANTIC_CANDIDATES)) {
                final int currentRank = rank++;
                if (isExternalSource(hit.sourceType())) {
                    connectors.findByExternalId(projectId, hit.sourceIdentifier()).ifPresent(row -> {
                        MaterialHit material = externalHit(row, hit.content(), query, terms);
                        mergeSemantic(out, "EXT:" + row.id(), "EXT:" + row.id(), material, null, row, query, terms, currentRank);
                    });
                } else {
                    MaterialHit material = nativeHit(hit, query, terms);
                    mergeSemantic(out, "HUB:" + hit.chunkId(), "HUB-DOC:" + hit.documentId(), material, hit, null, query, terms, currentRank);
                }
            }
        } catch (RuntimeException ignored) {
            // Embedding/AI failure must never make ordinary document search unavailable.
        }
    }

    private void addNativeLexicalCandidates(long projectId,
                                            String query,
                                            List<String> terms,
                                            Map<String, Candidate> out) {
        int rank = 1;
        for (SearchHit hit : lexicalSearch.searchNative(projectId, query, LEXICAL_CANDIDATES)) {
            MaterialHit material = nativeHit(hit, query, terms);
            mergeLexical(out, "HUB:" + hit.chunkId(), "HUB-DOC:" + hit.documentId(), material, hit, null, query, terms, rank++);
        }
    }

    private void addExternalLexicalCandidates(long projectId,
                                              String query,
                                              List<String> terms,
                                              Map<String, Candidate> out) {
        int rank = 1;
        for (ConnectorRepository.ExternalSearchRow row : connectors.search(projectId, query, LEXICAL_CANDIDATES)) {
            MaterialHit material = externalHit(row, row.content(), query, terms);
            mergeLexical(out, "EXT:" + row.id(), "EXT:" + row.id(), material, null, row, query, terms, rank++);
        }
    }

    private void mergeSemantic(Map<String, Candidate> out,
                               String key,
                               String sourceKey,
                               MaterialHit hit,
                               SearchHit nativeSeed,
                               ConnectorRepository.ExternalSearchRow externalRow,
                               String query,
                               List<String> terms,
                               int rank) {
        Candidate current = out.computeIfAbsent(key, ignored -> new Candidate(hit, sourceKey));
        current.attach(nativeSeed, externalRow);
        current.semanticRank = Math.min(current.semanticRank, rank);
        current.exactBonus = Math.max(current.exactBonus, exactBonus(hit, query, terms));
    }

    private void mergeLexical(Map<String, Candidate> out,
                              String key,
                              String sourceKey,
                              MaterialHit hit,
                              SearchHit nativeSeed,
                              ConnectorRepository.ExternalSearchRow externalRow,
                              String query,
                              List<String> terms,
                              int rank) {
        Candidate current = out.computeIfAbsent(key, ignored -> new Candidate(hit, sourceKey));
        current.attach(nativeSeed, externalRow);
        current.lexicalRank = Math.min(current.lexicalRank, rank);
        current.exactBonus = Math.max(current.exactBonus, exactBonus(hit, query, terms));
    }

    private List<MaterialHit> recommendDocuments(List<Candidate> ranked, SearchQueryPlan plan, int offset) {
        Map<String, Integer> relatedCounts = new HashMap<>();
        for (Candidate candidate : ranked) relatedCounts.merge(candidate.sourceKey, 1, Integer::sum);
        LinkedHashMap<String, Candidate> bestBySource = new LinkedHashMap<>();
        for (Candidate candidate : ranked) bestBySource.putIfAbsent(candidate.sourceKey, candidate);

        List<MaterialHit> result = new ArrayList<>();
        int skipped = 0;
        for (Candidate candidate : bestBySource.values()) {
            if (skipped < offset) { skipped++; continue; }
            MaterialHit hit = withRecommendation(candidate, offset + result.size() + 1, plan);
            int count = relatedCounts.getOrDefault(candidate.sourceKey, 1);
            if (count > 1) {
                hit = recommend(hit, hit.recommendationRank(), hit.matchType(),
                        hit.recommendationReason() + " 같은 파일에서 관련 위치 " + count + "곳을 확인했습니다.");
            }
            result.add(hit);
            if (result.size() >= MAX_RESULTS) break;
        }
        return result;
    }

    private MaterialHit nativeHit(SearchHit hit, String query, List<String> terms) {
        return new MaterialHit(
                hit.chunkId(), "HUB", nativeSourceLabel(hit.sourceType()), "DOCUMENT", hit.documentName(),
                joinLocation(hit.documentName(), hit.paragraphRef()), excerpt(hit.content(), query, terms, 620),
                blankTo(hit.author(), ""), "", hit.sourceCreatedAt(), 0, "", ""
        );
    }

    private MaterialHit externalHit(ConnectorRepository.ExternalSearchRow row,
                                    String preferredSnippet,
                                    String query,
                                    List<String> terms) {
        String connectorType = connectorType(row.externalId());
        String location = externalLocation(row);
        String content = blankTo(preferredSnippet, blankTo(row.content(), row.title()));
        return new MaterialHit(
                -row.id(), connectorType, sourceLabel(connectorType), row.itemType(),
                blankTo(row.title(), "자료"), location, excerpt(content, query, terms, 620),
                blankTo(row.author(), ""), safeHttps(row.sourceUrl()), row.sourceCreatedAt(),
                0, "", ""
        );
    }

    private String externalLocation(ConnectorRepository.ExternalSearchRow row) {
        String connectorType = connectorType(row.externalId());
        JsonNode metadata = metadata(row.rawMetadata());
        String location = metadata.path("location").asText("");
        return location.isBlank() ? fallbackLocation(connectorType, row.title()) : location;
    }

    private MaterialHit withRecommendation(Candidate candidate, int rank, SearchQueryPlan plan) {
        MaterialHit hit = candidate.hit;
        return recommend(hit, rank, matchType(candidate, plan), recommendationReason(candidate, hit, plan));
    }

    private static MaterialHit recommend(MaterialHit hit, int rank, String matchType, String reason) {
        return new MaterialHit(
                hit.evidenceId(), hit.sourceType(), hit.sourceLabel(), hit.itemType(), hit.title(),
                hit.location(), hit.snippet(), hit.author(), hit.sourceUrl(), hit.sourceCreatedAt(),
                rank, matchType, reason
        );
    }

    private static String matchType(Candidate candidate, SearchQueryPlan plan) {
        if (candidate.metadataRank < Integer.MAX_VALUE && plan.hasMetadataFilters()) return "조건에 맞는 자료";
        if (candidate.ruleRank < Integer.MAX_VALUE) return "직접 지정한 자료";
        if (candidate.templateRank < Integer.MAX_VALUE) return "원본 양식과 비슷한 자료";
        if (candidate.exactBonus >= 0.060d) return "제목/파일명 일치";
        if (candidate.semanticRank < Integer.MAX_VALUE && candidate.lexicalRank < Integer.MAX_VALUE) return "의미+키워드";
        if (candidate.semanticRank < Integer.MAX_VALUE) return "의미 유사도";
        return "키워드 일치";
    }

    private static String recommendationReason(Candidate candidate, MaterialHit hit, SearchQueryPlan plan) {
        boolean hub = "HUB".equalsIgnoreCase(hit.sourceType());
        String freshness = hub ? " 최신 버전에서 찾았습니다." : " 원본 위치를 함께 확인할 수 있습니다.";
        if (candidate.metadataRank < Integer.MAX_VALUE && plan.hasMetadataFilters())
            return metadataReason(plan) + freshness;
        if (candidate.ruleRank < Integer.MAX_VALUE) return "관리자가 직접 지정한 자료와 일치합니다." + freshness;
        if (candidate.templateRank < Integer.MAX_VALUE) return "기준 자료 '" + blankTo(candidate.templateName, "원본 양식") + "'의 항목과 내용이 비슷해 같은 종류의 자료로 판단했습니다. 파일 이름이 달라도 찾을 수 있습니다." + freshness;
        if (candidate.exactBonus >= 0.060d) return "제목이나 파일명이 질문과 직접 일치합니다." + freshness;
        if (candidate.semanticRank < Integer.MAX_VALUE && candidate.lexicalRank < Integer.MAX_VALUE)
            return "자연어 의미와 핵심 단어가 함께 가까운 자료입니다." + freshness;
        if (candidate.semanticRank < Integer.MAX_VALUE) return "질문의 자연어 의미와 가까운 자료입니다." + freshness;
        return "검색어가 제목·본문·위치에 직접 포함된 자료입니다." + freshness;
    }

    private static double exactBonus(MaterialHit hit, String fullQuery, List<String> terms) {
        String title = SearchText.lower(hit.title());
        String body = SearchText.lower(hit.snippet());
        String location = SearchText.lower(hit.location());
        String author = SearchText.lower(hit.author());
        String full = SearchText.lower(fullQuery);
        double score = 0.0d;
        if (!full.isBlank()) {
            if (title.equals(full)) score += 0.090d;
            else if (title.contains(full)) score += 0.065d;
            if (location.contains(full)) score += 0.030d;
            if (body.contains(full)) score += 0.025d;
        }
        for (String term : terms) {
            String token = SearchText.lower(term);
            if (token.equals(full)) continue;
            if (title.contains(token)) score += 0.010d;
            if (location.contains(token)) score += 0.007d;
            if (body.contains(token)) score += 0.004d;
            if (author.contains(token)) score += 0.003d;
        }
        return Math.min(score, 0.180d);
    }

    private static double rerankBonus(MaterialHit hit, SearchQueryPlan plan, List<String> terms) {
        String title = SearchText.lower(hit.title());
        String body = SearchText.lower(hit.snippet());
        String location = SearchText.lower(hit.location());
        String author = SearchText.lower(hit.author());
        double score = 0.0d;

        if (!plan.exactFilename().isBlank()) {
            String wanted = SearchText.lower(plan.exactFilename());
            if (title.equals(wanted)) score += 0.220d;
            else if (title.contains(wanted)) score += 0.120d;
            else score -= 0.050d;
        }
        if (!plan.author().isBlank()) {
            score += author.contains(SearchText.lower(plan.author())) ? 0.080d : -0.060d;
        }
        if (plan.hasDateFilter()) {
            if (hit.sourceCreatedAt() == null) score -= 0.035d;
            else if (dateMatches(hit.sourceCreatedAt(), plan)) score += 0.070d;
            else score -= 0.100d;
        }
        if (!plan.sourceTypes().isEmpty()) {
            String source = normalizedSourceType(hit);
            score += plan.sourceTypes().contains(source) ? 0.085d : -0.100d;
        }

        int matched = 0;
        for (String term : terms) {
            String token = SearchText.lower(term);
            if (token.length() < 2 || token.equals(SearchText.lower(plan.searchText()))) continue;
            if (title.contains(token)) { score += 0.015d; matched++; }
            else if (location.contains(token)) { score += 0.009d; matched++; }
            else if (author.contains(token)) { score += 0.008d; matched++; }
            else if (body.contains(token)) { score += 0.005d; matched++; }
            if (matched >= 5) break;
        }
        return Math.max(-0.180d, Math.min(score, 0.260d));
    }

    private static boolean dateMatches(java.time.OffsetDateTime value, SearchQueryPlan plan) {
        if (value == null) return false;
        java.time.Instant instant = value.toInstant();
        if (plan.fromInclusive() != null && instant.isBefore(plan.fromInclusive().toInstant())) return false;
        return plan.toExclusive() == null || instant.isBefore(plan.toExclusive().toInstant());
    }

    private static String normalizedSourceType(MaterialHit hit) {
        if ("HUB".equalsIgnoreCase(hit.sourceType())) {
            String label = SearchText.lower(hit.sourceLabel());
            if (label.contains("회의")) return "MEETING_TRANSCRIPT";
            if (label.contains("pc")) return "LOCAL_PC";
            return "FILE";
        }
        return hit.sourceType() == null ? "" : hit.sourceType().toUpperCase(Locale.ROOT);
    }

    private static String metadataReason(SearchQueryPlan plan) {
        List<String> parts = new ArrayList<>();
        if (!plan.author().isBlank()) parts.add("작성/등록자 " + plan.author());
        if (plan.hasDateFilter()) parts.add("요청한 기간");
        if (!plan.sourceTypes().isEmpty()) parts.add("자료 종류");
        return String.join(" · ", parts) + " 조건을 우선 적용해 찾았습니다.";
    }

    private static double rrf(int rank, double weight) {
        return rank == Integer.MAX_VALUE ? 0.0d : weight / (RRF_K + rank);
    }

    private static String nativeSourceLabel(String sourceType) {
        String type = sourceType == null ? "" : sourceType.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "MEETING_TRANSCRIPT" -> "회의 녹음 기록";
            case "MANUAL_TEXT" -> "직접 입력";
            case "LOCAL_PC" -> "내 PC 파일";
            default -> "업로드한 자료";
        };
    }

    private static boolean isExternalSource(String sourceType) {
        return "GITHUB".equalsIgnoreCase(sourceType)
                || "GOOGLE_DRIVE".equalsIgnoreCase(sourceType)
                || "SLACK".equalsIgnoreCase(sourceType)
                || "NOTION".equalsIgnoreCase(sourceType);
    }

    private JsonNode metadata(String raw) {
        try {
            return json.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (Exception ignored) {
            return json.createObjectNode();
        }
    }

    private static String connectorType(String namespacedExternalId) {
        if (namespacedExternalId == null) return "EXTERNAL";
        int colon = namespacedExternalId.indexOf(':');
        return colon > 0 ? namespacedExternalId.substring(0, colon).toUpperCase(Locale.ROOT) : "EXTERNAL";
    }

    private static String sourceLabel(String type) {
        return switch (type) {
            case "SLACK" -> "Slack";
            case "GOOGLE_DRIVE" -> "Google Drive";
            case "GITHUB" -> "GitHub";
            case "NOTION" -> "Notion";
            default -> "외부 자료";
        };
    }

    private static String fallbackLocation(String type, String title) {
        return sourceLabel(type) + " / " + blankTo(title, "자료");
    }

    private static String joinLocation(String document, String paragraph) {
        if (paragraph == null || paragraph.isBlank()) return blankTo(document, "Hub 문서");
        return blankTo(document, "Hub 문서") + " / " + paragraph;
    }

    private static String requiredQuery(String value) {
        String normalized = UnicodeText.nfc(value == null ? "" : value).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("자료 검색어가 필요합니다.");
        if (normalized.length() > 1000) throw new IllegalArgumentException("자료 검색어가 너무 깁니다.");
        return normalized;
    }

    private static String safeHttps(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        return trimmed.regionMatches(true, 0, "https://", 0, 8) ? trimmed : "";
    }

    private static String excerpt(String value, String query, List<String> terms, int max) {
        return contextExcerpt(value, query, terms, max);
    }

    private static String contextExcerpt(String value, String query, List<String> terms, int max) {
        String normalized = blankTo(value, "").replaceAll("\\s+", " ").trim();
        if (max <= 0 || normalized.isBlank()) return "";
        if (normalized.length() <= max) return normalized;
        String lower = normalized.toLowerCase(Locale.ROOT);
        int match = lower.indexOf(SearchText.lower(query));
        if (match < 0) {
            for (String term : terms) {
                match = lower.indexOf(SearchText.lower(term));
                if (match >= 0) break;
            }
        }
        if (match < 0) return normalized.substring(0, max) + "…";
        int start = Math.max(0, match - max / 3);
        int end = Math.min(normalized.length(), start + max);
        if (end - start < max && start > 0) start = Math.max(0, end - max);
        return (start > 0 ? "…" : "") + normalized.substring(start, end) + (end < normalized.length() ? "…" : "");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class Candidate {
        private final MaterialHit hit;
        private final String sourceKey;
        private int ruleRank = Integer.MAX_VALUE;
        private int semanticRank = Integer.MAX_VALUE;
        private int lexicalRank = Integer.MAX_VALUE;
        private int templateRank = Integer.MAX_VALUE;
        private int metadataRank = Integer.MAX_VALUE;
        private double exactBonus;
        private double rerankBonus;
        private String ruleName;
        private String templateName;
        private SearchHit nativeSeed;
        private ConnectorRepository.ExternalSearchRow externalRow;

        private Candidate(MaterialHit hit, String sourceKey) {
            this.hit = hit;
            this.sourceKey = sourceKey;
        }

        private void attach(SearchHit nativeSeed, ConnectorRepository.ExternalSearchRow externalRow) {
            if (this.nativeSeed == null && nativeSeed != null) this.nativeSeed = nativeSeed;
            if (this.externalRow == null && externalRow != null) this.externalRow = externalRow;
        }

        private MaterialHit hit() { return hit; }
        private SearchHit nativeSeed() { return nativeSeed; }
        private ConnectorRepository.ExternalSearchRow externalRow() { return externalRow; }

        private double score(SearchQueryPlan plan) {
            return rrf(ruleRank, plan.ruleWeight())
                    + rrf(templateRank, plan.templateWeight())
                    + rrf(semanticRank, plan.semanticWeight())
                    + rrf(lexicalRank, plan.lexicalWeight())
                    + rrf(metadataRank, plan.metadataWeight())
                    + exactBonus + rerankBonus;
        }
    }
}
