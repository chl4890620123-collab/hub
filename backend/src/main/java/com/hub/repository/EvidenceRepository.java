package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class EvidenceRepository {
    private final JdbcTemplate jdbc;

    public EvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Evidence keeps a text snapshot so later re-chunking cannot rewrite historical proof. */
    public long createDocumentEvidence(long versionId, Long chunkId, String quote, String contentHash) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO evidence(version_id,chunk_id,evidence_text,content_hash) VALUES(?,?,?,?)",
                    new String[]{"id"}
            );
            ps.setLong(1, versionId);
            if (chunkId == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, chunkId);
            ps.setString(3, quote);
            ps.setString(4, contentHash);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Evidence id was not generated");
        return key.getKey().longValue();
    }

    public long createTranscriptEvidence(long segmentId, String quote, String contentHash) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO evidence(transcript_segment_id,evidence_text,content_hash) VALUES(?,?,?)",
                    new String[]{"id"}
            );
            ps.setLong(1, segmentId);
            ps.setString(2, quote);
            ps.setString(3, contentHash);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Evidence id was not generated");
        return key.getKey().longValue();
    }

    public void linkTodo(long todoId, long evidenceId) {
        jdbc.update("INSERT INTO todo_evidence(todo_id,evidence_id) VALUES(?,?)", todoId, evidenceId);
    }


    /** Merge-only operation used when ADMIN decides an AI candidate is the same real-world task. */
    public int mergeTodoEvidence(long fromTodoId, long intoTodoId) {
        int linked = jdbc.update("""
                INSERT INTO todo_evidence(todo_id,evidence_id)
                SELECT ?,src.evidence_id FROM todo_evidence src
                WHERE src.todo_id=?
                  AND NOT EXISTS (SELECT 1 FROM todo_evidence dst WHERE dst.todo_id=? AND dst.evidence_id=src.evidence_id)
                """, intoTodoId, fromTodoId, intoTodoId);
        return linked;
    }

    public void linkChange(long changeItemId, long evidenceId, String side) {
        jdbc.update(
                "INSERT INTO change_evidence(change_item_id,evidence_id,evidence_side) VALUES(?,?,?)",
                changeItemId, evidenceId, side
        );
    }

    public List<EvidenceView> forTodo(long todoId) {
        return queryEvidence(
                """
                SELECT e.id,e.version_id,e.chunk_id,e.evidence_text,e.content_hash,d.original_name,c.paragraph_ref,c.page_no,
                       m.title meeting_title,ts.start_ms,ts.end_ms,ts.speaker
                FROM todo_evidence link
                JOIN evidence e ON e.id=link.evidence_id
                LEFT JOIN document_version v ON v.id=e.version_id
                LEFT JOIN document d ON d.id=v.document_id
                LEFT JOIN document_chunk c ON c.id=e.chunk_id
                LEFT JOIN transcript_segment ts ON ts.id=e.transcript_segment_id
                LEFT JOIN meeting m ON m.id=ts.meeting_id
                WHERE link.todo_id=?
                ORDER BY e.id
                """,
                todoId
        );
    }

    public List<EvidenceView> forDecision(long decisionId) {
        return queryEvidence(
                """
                SELECT e.id,e.version_id,e.chunk_id,e.evidence_text,e.content_hash,d.original_name,c.paragraph_ref,c.page_no,
                       m.title meeting_title,ts.start_ms,ts.end_ms,ts.speaker
                FROM decision_evidence link
                JOIN evidence e ON e.id=link.evidence_id
                LEFT JOIN document_version v ON v.id=e.version_id
                LEFT JOIN document d ON d.id=v.document_id
                LEFT JOIN document_chunk c ON c.id=e.chunk_id
                LEFT JOIN transcript_segment ts ON ts.id=e.transcript_segment_id
                LEFT JOIN meeting m ON m.id=ts.meeting_id
                WHERE link.decision_id=?
                ORDER BY e.id
                """,
                decisionId
        );
    }

    public List<ChangeEvidenceView> forChange(long changeItemId) {
        return jdbc.query(
                """
                SELECT link.evidence_side,e.id,e.version_id,e.chunk_id,e.evidence_text,e.content_hash,d.original_name,c.paragraph_ref,c.page_no
                FROM change_evidence link
                JOIN evidence e ON e.id=link.evidence_id
                LEFT JOIN document_version v ON v.id=e.version_id
                LEFT JOIN document d ON d.id=v.document_id
                LEFT JOIN document_chunk c ON c.id=e.chunk_id
                WHERE link.change_item_id=?
                ORDER BY CASE WHEN link.evidence_side='BEFORE' THEN 0 ELSE 1 END,e.id
                """,
                (rs, n) -> {
                    int pageValue = rs.getInt("page_no");
                    Integer pageNo = rs.wasNull() ? null : pageValue;
                    long versionValue = rs.getLong("version_id");
                    Long versionId = rs.wasNull() ? null : versionValue;
                    long chunkValue = rs.getLong("chunk_id");
                    Long chunkId = rs.wasNull() ? null : chunkValue;
                    return new ChangeEvidenceView(
                            rs.getString("evidence_side"),
                            rs.getLong("id"),
                            versionId,
                            chunkId,
                            rs.getString("evidence_text"),
                            rs.getString("content_hash"),
                            rs.getString("original_name"),
                            rs.getString("paragraph_ref"),
                            pageNo
                    );
                },
                changeItemId
        );
    }

    private List<EvidenceView> queryEvidence(String sql, long entityId) {
        return jdbc.query(
                sql,
                (rs, n) -> {
                    long startValue = rs.getLong("start_ms");
                    Long startMs = rs.wasNull() ? null : startValue;
                    long endValue = rs.getLong("end_ms");
                    Long endMs = rs.wasNull() ? null : endValue;
                    int pageValue = rs.getInt("page_no");
                    Integer pageNo = rs.wasNull() ? null : pageValue;
                    long versionValue = rs.getLong("version_id");
                    Long versionId = rs.wasNull() ? null : versionValue;
                    long chunkValue = rs.getLong("chunk_id");
                    Long chunkId = rs.wasNull() ? null : chunkValue;
                    return new EvidenceView(
                            rs.getLong("id"),
                            versionId,
                            chunkId,
                            rs.getString("evidence_text"),
                            rs.getString("content_hash"),
                            rs.getString("original_name"),
                            rs.getString("paragraph_ref"),
                            pageNo,
                            rs.getString("meeting_title"),
                            startMs,
                            endMs,
                            rs.getString("speaker")
                    );
                },
                entityId
        );
    }

    public record ChangeEvidenceView(
            String side,
            long id,
            Long versionId,
            Long chunkId,
            String quote,
            String contentHash,
            String documentName,
            String paragraphRef,
            Integer pageNo
    ) {
    }

    public record EvidenceView(
            long id,
            Long versionId,
            Long chunkId,
            String quote,
            String contentHash,
            String documentName,
            String paragraphRef,
            Integer pageNo,
            String meetingTitle,
            Long startMs,
            Long endMs,
            String speaker
    ) {
    }
}
