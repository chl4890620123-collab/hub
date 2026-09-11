package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.SearchHit;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Profile("postgresql")
public class PostgresVectorIndexService implements VectorIndexService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PostgresVectorIndexService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void store(long chunkId, List<Float> vector) {
        String value = vectorLiteral(vector);
        try {
            jdbc.update(
                    "UPDATE document_chunk SET embedding_json=?, embedding_vector=CAST(? AS vector) WHERE id=?",
                    json.writeValueAsString(vector), value, chunkId
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store pgvector embedding", e);
        }
    }

    /**
     * pgvector HNSW ANN retrieval. Historical versions stay in the database for audit/evidence,
     * but default search only considers the latest version of each active document.
     */
    public List<SearchHit> nearest(long projectId, List<Float> queryVector, int limit) {
        String value = vectorLiteral(queryVector);
        return jdbc.query(
                """
                SELECT c.id chunk_id,c.version_id,d.id document_id,v.version_no,d.source_type,d.source_identifier,
                       d.original_name,c.paragraph_ref,c.content,u.display_name author,v.created_at source_created_at
                FROM document_chunk c
                JOIN document_version v ON v.id=c.version_id
                JOIN document d ON d.id=v.document_id
                JOIN app_user u ON u.id=d.created_by
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND c.embedding_vector IS NOT NULL
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                ORDER BY c.embedding_vector <=> CAST(? AS vector)
                LIMIT ?
                """,
                (rs, n) -> new SearchHit(
                        rs.getLong("chunk_id"), rs.getLong("version_id"), rs.getLong("document_id"),
                        rs.getInt("version_no"), rs.getString("source_type"), rs.getString("source_identifier"),
                        rs.getString("original_name"), rs.getString("paragraph_ref"), rs.getString("content"),
                        rs.getString("author"), timestamp(rs.getTimestamp("source_created_at"))
                ),
                projectId, value, Math.max(1, Math.min(limit, 200))
        );
    }

    private String vectorLiteral(List<Float> vector) {
        return "[" + vector.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private static OffsetDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

}
