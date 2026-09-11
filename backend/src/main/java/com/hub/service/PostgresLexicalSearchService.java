package com.hub.service;

import com.hub.model.SearchHit;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/** PostgreSQL full-text side of hybrid retrieval; document title keeps an exact/substring path. */
@Service
@Profile("postgresql")
public class PostgresLexicalSearchService implements LexicalSearchService {
    private final JdbcTemplate jdbc;

    public PostgresLexicalSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchHit> searchNative(long projectId, String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) return List.of();
        String lower = normalized.toLowerCase(Locale.ROOT);
        return jdbc.query(
                """
                WITH search_query AS (
                  SELECT websearch_to_tsquery('simple'::regconfig, ?) AS q
                )
                SELECT c.id chunk_id,c.version_id,d.id document_id,v.version_no,d.source_type,d.source_identifier,
                       d.original_name,c.paragraph_ref,c.content,u.display_name author,v.created_at source_created_at
                FROM document_chunk c
                JOIN document_version v ON v.id=c.version_id
                JOIN document d ON d.id=v.document_id
                JOIN app_user u ON u.id=d.created_by
                CROSS JOIN search_query sq
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND d.source_type NOT IN ('GITHUB','GOOGLE_DRIVE','SLACK','NOTION')
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                  AND (c.search_vector @@ sq.q OR lower(COALESCE(d.original_name,'')) LIKE ?)
                ORDER BY
                  CASE
                    WHEN lower(COALESCE(d.original_name,'')) = ? THEN 0
                    WHEN lower(COALESCE(d.original_name,'')) LIKE ? THEN 1
                    ELSE 2
                  END,
                  ts_rank_cd(c.search_vector, sq.q) DESC,
                  v.created_at DESC,
                  c.chunk_index ASC
                LIMIT ?
                """,
                (rs, n) -> new SearchHit(
                        rs.getLong("chunk_id"), rs.getLong("version_id"), rs.getLong("document_id"),
                        rs.getInt("version_no"), rs.getString("source_type"), rs.getString("source_identifier"),
                        rs.getString("original_name"), rs.getString("paragraph_ref"), rs.getString("content"),
                        rs.getString("author"), timestamp(rs.getTimestamp("source_created_at"))
                ),
                normalized,
                projectId,
                "%" + lower + "%",
                lower,
                "%" + lower + "%",
                Math.max(1, Math.min(limit, 200))
        );
    }

    private static OffsetDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

}
