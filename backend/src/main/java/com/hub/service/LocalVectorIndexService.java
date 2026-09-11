package com.hub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.SearchHit;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Profile("!postgresql")
public class LocalVectorIndexService implements VectorIndexService {
    private static final int MAX_LOCAL_VECTOR_SCAN = 2000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public LocalVectorIndexService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void store(long chunkId, List<Float> vector) {
        try {
            jdbc.update("UPDATE document_chunk SET embedding_json=? WHERE id=?", json.writeValueAsString(vector), chunkId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * H2/local mode mirrors production semantics with a bounded cosine scan over latest versions.
     * This is intentionally for local/demo scale; PostgreSQL uses HNSW ANN for real datasets.
     */
    public List<SearchHit> nearest(long projectId, List<Float> queryVector, int limit) {
        if (queryVector == null || queryVector.isEmpty()) return List.of();
        List<Row> rows = jdbc.query(
                """
                SELECT c.id chunk_id,c.version_id,d.id document_id,v.version_no,d.source_type,d.source_identifier,
                       d.original_name,c.paragraph_ref,c.content,u.display_name author,v.created_at source_created_at,c.embedding_json
                FROM document_chunk c
                JOIN document_version v ON v.id=c.version_id
                JOIN document d ON d.id=v.document_id
                JOIN app_user u ON u.id=d.created_by
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND c.embedding_json IS NOT NULL
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                ORDER BY v.created_at DESC,c.id DESC
                LIMIT ?
                """,
                (rs, n) -> new Row(
                        new SearchHit(
                                rs.getLong("chunk_id"), rs.getLong("version_id"), rs.getLong("document_id"),
                                rs.getInt("version_no"), rs.getString("source_type"), rs.getString("source_identifier"),
                                rs.getString("original_name"), rs.getString("paragraph_ref"), rs.getString("content"),
                                rs.getString("author"), timestamp(rs.getTimestamp("source_created_at"))
                        ),
                        rs.getString("embedding_json")
                ),
                projectId, MAX_LOCAL_VECTOR_SCAN
        );
        List<Scored> scored = new ArrayList<>();
        for (Row row : rows) {
            try {
                List<Float> vector = json.readValue(row.embeddingJson(), new TypeReference<List<Float>>() {});
                scored.add(new Scored(row.hit(), cosine(queryVector, vector)));
            } catch (Exception ignored) {
                // A malformed local embedding should not take the entire search path down.
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .map(Scored::hit)
                .toList();
    }

    private static double cosine(List<Float> a, List<Float> b) {
        if (a.size() != b.size() || a.isEmpty()) return -1;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i), y = b.get(i);
            dot += x * y;
            aa += x * x;
            bb += y * y;
        }
        return aa == 0 || bb == 0 ? -1 : dot / (Math.sqrt(aa) * Math.sqrt(bb));
    }

    private record Row(SearchHit hit, String embeddingJson) {}
    private record Scored(SearchHit hit, double score) {}

    private static OffsetDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

}
