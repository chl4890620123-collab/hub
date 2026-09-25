// document identity/version lookup is centralized here so duplicate uploads reuse one immutable version.
package com.hub.repository;

import com.hub.model.DocumentVersionRef;
import com.hub.model.SearchHit;
import com.hub.util.SearchText;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LatestVersion(long id, String sha256, int versionNo) {}

    public long createDocument(long projectId,
                               String sourceType,
                               String sourceIdentifier,
                               String originalName,
                               String storagePath,
                               long userId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO document(project_id,source_type,source_identifier,original_name,storage_path,created_by)
                    VALUES(?,?,?,?,?,?)
                    """,
                    new String[]{"id"}
            );
            ps.setLong(1, projectId);
            ps.setString(2, sourceType);
            ps.setString(3, sourceIdentifier);
            ps.setString(4, originalName);
            ps.setString(5, storagePath);
            ps.setLong(6, userId);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Document id was not generated");
        return key.getKey().longValue();
    }


    public Optional<Long> findVersionByHash(long projectId, String sourceType, String sha256) {
        List<Long> ids = jdbc.query(
                """
                SELECT v.id FROM document_version v
                JOIN document d ON d.id=v.document_id
                WHERE d.project_id=? AND d.source_type=? AND v.sha256=?
                  AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND v.full_text<>?
                ORDER BY v.id DESC LIMIT 1
                """,
                (rs,n)->rs.getLong(1), projectId, sourceType, sha256, ARCHIVED_CONTENT_PLACEHOLDER);
        return ids.stream().findFirst();
    }

    public Optional<Long> findDocumentId(long projectId, String sourceType, String sourceIdentifier) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM document WHERE project_id=? AND source_type=? AND source_identifier=?",
                (rs, n) -> rs.getLong(1),
                projectId, sourceType, sourceIdentifier
        );
        return ids.stream().findFirst();
    }

    public boolean isSourceDeleted(long projectId, String sourceType, String sourceIdentifier) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM document WHERE project_id=? AND source_type=? AND source_identifier=? AND source_deleted=TRUE",
                Integer.class, projectId, sourceType, sourceIdentifier);
        return count != null && count > 0;
    }

    public boolean isSourceArchived(long projectId, String sourceType, String sourceIdentifier) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM document WHERE project_id=? AND source_type=? AND source_identifier=? AND archived=TRUE AND source_deleted=FALSE",
                Integer.class, projectId, sourceType, sourceIdentifier);
        return count != null && count > 0;
    }

    /**
     * Serializes first-time source creation inside one project. This prevents two simultaneous uploads
     * of the same new source from racing into the UNIQUE(project_id, source_type, source_identifier)
     * constraint before a document row exists.
     */
    public void lockProject(long projectId) {
        jdbc.queryForObject("SELECT id FROM project WHERE id=? FOR UPDATE", Long.class, projectId);
    }

    /** Serializes version-number allocation for one document. Must be called inside a transaction. */
    public void lockDocument(long documentId) {
        jdbc.queryForObject("SELECT id FROM document WHERE id=? FOR UPDATE", Long.class, documentId);
    }

    /** Serializes AI analysis for one immutable version. Must be called inside a transaction. */
    public void lockVersion(long versionId) {
        jdbc.queryForObject("SELECT id FROM document_version WHERE id=? FOR UPDATE", Long.class, versionId);
    }

    public Optional<LatestVersion> latestVersion(long documentId) {
        List<LatestVersion> rows = jdbc.query(
                """
                SELECT id,sha256,version_no
                FROM document_version
                WHERE document_id=?
                ORDER BY version_no DESC
                LIMIT 1
                """,
                (rs, n) -> new LatestVersion(
                        rs.getLong("id"),
                        rs.getString("sha256"),
                        rs.getInt("version_no")
                ),
                documentId
        );
        return rows.stream().findFirst();
    }

    public void updateSourceMetadata(long documentId, String originalName, String storagePath) {
        jdbc.update(
                """
                UPDATE document
                SET original_name=?,storage_path=?,archived=FALSE,source_deleted=FALSE,archived_at=NULL
                WHERE id=?
                """,
                originalName, storagePath, documentId
        );
    }

    public long createVersion(long documentId, String sha256, String fullText, String status) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM document_version WHERE document_id=?",
                Integer.class,
                documentId
        );
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO document_version(document_id,version_no,sha256,full_text,parse_status)
                    VALUES(?,?,?,?,?)
                    """,
                    new String[]{"id"}
            );
            ps.setLong(1, documentId);
            ps.setInt(2, next == null ? 1 : next);
            ps.setString(3, sha256);
            ps.setString(4, fullText);
            ps.setString(5, status);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Document version id was not generated");
        return key.getKey().longValue();
    }

    public long createChunk(long versionId, int index, String paragraphRef, String content) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO document_chunk(version_id,chunk_index,paragraph_ref,content)
                    VALUES(?,?,?,?)
                    """,
                    new String[]{"id"}
            );
            ps.setLong(1, versionId);
            ps.setInt(2, index);
            ps.setString(3, paragraphRef);
            ps.setString(4, content);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Chunk id was not generated");
        return key.getKey().longValue();
    }

    public void markEmbeddingStatus(long versionId, String status, String error, boolean incrementAttempt) {
        jdbc.update(
                "UPDATE document_version SET embedding_status=?,embedding_last_error=?,embedding_updated_at=CURRENT_TIMESTAMP,embedding_attempts=embedding_attempts+? WHERE id=?",
                status, error == null || error.isBlank() ? null : limit(error, 2000), incrementAttempt ? 1 : 0, versionId
        );
    }

    public record EmbeddingRetryCandidate(long versionId, long projectId, long documentId, int attempts) {}

    public List<EmbeddingRetryCandidate> embeddingRetryCandidates(int limit) {
        return jdbc.query(
                """
                SELECT v.id version_id,d.project_id,d.id document_id,v.embedding_attempts
                FROM document_version v
                JOIN document d ON d.id=v.document_id
                WHERE d.archived=FALSE AND d.source_deleted=FALSE
                  AND v.embedding_status IN ('PENDING','FAILED')
                  AND v.embedding_attempts < 20
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                ORDER BY COALESCE(v.embedding_updated_at,v.created_at) ASC,v.id ASC
                LIMIT ?
                """,
                (rs,n) -> new EmbeddingRetryCandidate(rs.getLong("version_id"),rs.getLong("project_id"),rs.getLong("document_id"),rs.getInt("embedding_attempts")),
                Math.max(1, Math.min(limit, 100))
        );
    }

    public List<EmbeddingRetryCandidate> embeddingRetryCandidates(long projectId, int limit) {
        return jdbc.query(
                """
                SELECT v.id version_id,d.project_id,d.id document_id,v.embedding_attempts
                FROM document_version v
                JOIN document d ON d.id=v.document_id
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND v.embedding_status IN ('PENDING','FAILED')
                  AND v.embedding_attempts < 20
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                ORDER BY COALESCE(v.embedding_updated_at,v.created_at) ASC,v.id ASC
                LIMIT ?
                """,
                (rs,n) -> new EmbeddingRetryCandidate(rs.getLong("version_id"),rs.getLong("project_id"),rs.getLong("document_id"),rs.getInt("embedding_attempts")),
                projectId, Math.max(1, Math.min(limit, 100))
        );
    }

    public Map<String,Object> embeddingHealth(long projectId) {
        return jdbc.queryForMap(
                """
                SELECT COUNT(*) total_versions,
                       SUM(CASE WHEN embedding_status='READY' THEN 1 ELSE 0 END) ready_versions,
                       SUM(CASE WHEN embedding_status='FAILED' THEN 1 ELSE 0 END) failed_versions,
                       SUM(CASE WHEN embedding_status='PENDING' THEN 1 ELSE 0 END) pending_versions
                FROM document_version v
                JOIN document d ON d.id=v.document_id
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                """, projectId
        );
    }

    private static String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Capped like the audit log: newest first, so a long-lived project's list stays a manageable page instead of every document ever imported. */
    public List<Map<String, Object>> listDocuments(long projectId) {
        return jdbc.queryForList(
                """
                SELECT d.id,d.original_name,d.source_type,d.source_identifier,d.archived,d.source_deleted,d.created_at,
                       MAX(v.version_no) latest_version,(d.storage_path IS NOT NULL) has_original,
                       EXISTS (
                         SELECT 1 FROM document_version lv
                         WHERE lv.document_id=d.id
                           AND lv.version_no=(SELECT MAX(lv2.version_no) FROM document_version lv2 WHERE lv2.document_id=d.id)
                           AND lv.full_text=?
                       ) content_purged
                FROM document d
                LEFT JOIN document_version v ON v.document_id=d.id
                WHERE d.project_id=? AND d.source_deleted=FALSE
                GROUP BY d.id,d.original_name,d.source_type,d.source_identifier,d.archived,d.source_deleted,d.created_at,d.storage_path
                ORDER BY d.id DESC
                LIMIT 500
                """,
                ARCHIVED_CONTENT_PLACEHOLDER, projectId
        );
    }

    public List<Map<String, Object>> listVersions(long documentId) {
        return jdbc.queryForList(
                """
                SELECT id,version_no,sha256,parse_status,summary,created_at
                FROM document_version
                WHERE document_id=?
                ORDER BY version_no DESC
                """,
                documentId
        );
    }

    public long projectIdForDocument(long documentId) {
        return jdbc.queryForObject("SELECT project_id FROM document WHERE id=?", Long.class, documentId);
    }

    public record DocumentMeta(long id, long projectId, String sourceType, String originalName,
                               boolean archived, boolean sourceDeleted) {}

    public Optional<DocumentMeta> findMeta(long documentId) {
        List<DocumentMeta> rows = jdbc.query(
                "SELECT id,project_id,source_type,original_name,archived,source_deleted FROM document WHERE id=?",
                (rs, n) -> new DocumentMeta(rs.getLong("id"), rs.getLong("project_id"), rs.getString("source_type"),
                        rs.getString("original_name"), rs.getBoolean("archived"), rs.getBoolean("source_deleted")),
                documentId);
        return rows.stream().findFirst();
    }

    /** storagePath is null for MANUAL_TEXT/MEETING_TRANSCRIPT sources - there is no original file to serve. */
    public record DocumentFile(String originalName, String storagePath) {}

    public Optional<DocumentFile> findFile(long documentId) {
        List<DocumentFile> rows = jdbc.query(
                "SELECT original_name, storage_path FROM document WHERE id=?",
                (rs, n) -> new DocumentFile(rs.getString("original_name"), rs.getString("storage_path")),
                documentId);
        return rows.stream().findFirst();
    }

    public void archive(long documentId) {
        jdbc.update("UPDATE document SET archived=TRUE,archived_at=CURRENT_TIMESTAMP WHERE id=?", documentId);
    }

    public void restore(long documentId) {
        var latest = latestVersion(documentId).orElseThrow(() -> new IllegalArgumentException("복원할 문서를 찾을 수 없습니다."));
        if (isVersionContentPurged(latest.id())) {
            throw new IllegalArgumentException("보관 기간이 지나 본문이 정리된 자료입니다. 원본 자료를 다시 등록해 주세요.");
        }
        if (jdbc.update("UPDATE document SET archived=FALSE,archived_at=NULL WHERE id=?", documentId) != 1) {
            throw new IllegalArgumentException("복원할 문서를 찾을 수 없습니다.");
        }
    }

    public boolean isVersionContentPurged(long versionId) {
        Boolean purged = jdbc.queryForObject(
                "SELECT full_text=? FROM document_version WHERE id=?",
                Boolean.class, ARCHIVED_CONTENT_PLACEHOLDER, versionId);
        return Boolean.TRUE.equals(purged);
    }

    /**
     * Permanently removes one document after detaching business records that are allowed to survive
     * without their source document. Evidence and version-comparison rows that directly depend on the
     * deleted document are removed; confirmed todos/decisions themselves remain.
     */
    @Transactional
    public void deletePermanently(long documentId) {
        Map<String,Object> identity = jdbc.queryForMap(
                "SELECT source_type,source_identifier FROM document WHERE id=?", documentId);
        String sourceType = String.valueOf(identity.getOrDefault("source_type", identity.get("SOURCE_TYPE")));
        boolean connectorSource = isConnectorSource(sourceType);

        jdbc.update("""
                UPDATE todo SET source_document_version_id=NULL
                WHERE source_document_version_id IN (
                  SELECT id FROM document_version WHERE document_id=?
                )
                """, documentId);
        jdbc.update("""
                UPDATE decision_candidate SET source_document_version_id=NULL
                WHERE source_document_version_id IN (
                  SELECT id FROM document_version WHERE document_id=?
                )
                """, documentId);
        jdbc.update("""
                UPDATE ai_run SET document_version_id=NULL
                WHERE document_version_id IN (
                  SELECT id FROM document_version WHERE document_id=?
                )
                """, documentId);
        jdbc.update("""
                DELETE FROM change_analysis
                WHERE before_version_id IN (SELECT id FROM document_version WHERE document_id=?)
                   OR after_version_id IN (SELECT id FROM document_version WHERE document_id=?)
                """, documentId, documentId);
        jdbc.update("""
                DELETE FROM evidence
                WHERE version_id IN (SELECT id FROM document_version WHERE document_id=?)
                   OR chunk_id IN (
                     SELECT c.id
                     FROM document_chunk c
                     JOIN document_version v ON v.id=c.version_id
                     WHERE v.document_id=?
                   )
                """, documentId, documentId);
        // Connector imports have two searchable copies: the normalized document and the
        // provider snapshot in external_item. Removing only the document lets the supposedly deleted
        // content reappear through connector lexical/RAG search, so remove every snapshot for the same
        // project + namespaced source identifier as part of the same DB transaction.
        jdbc.update("""
                DELETE FROM external_item
                WHERE imported_document_id=?
                   OR (
                     project_id=(SELECT project_id FROM document WHERE id=?)
                     AND external_id=(SELECT source_identifier FROM document WHERE id=?)
                     AND EXISTS (
                       SELECT 1 FROM document
                       WHERE id=? AND source_type IN ('GITHUB','GOOGLE_DRIVE','SLACK','NOTION')
                     )
                   )
                """, documentId, documentId, documentId, documentId);
        if (connectorSource) {
            // Keep only the external identity as a tombstone. All user/content-bearing rows are gone,
            // but the UNIQUE(project, source_type, source_identifier) identity remains so a later sync
            // cannot silently resurrect a source the user explicitly permanently deleted.
            jdbc.update("""
                    DELETE FROM document_chunk
                    WHERE version_id IN (SELECT id FROM document_version WHERE document_id=?)
                    """, documentId);
            jdbc.update("DELETE FROM document_version WHERE document_id=?", documentId);
            if (jdbc.update("""
                    UPDATE document
                    SET storage_path=NULL,archived=TRUE,source_deleted=TRUE,archived_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """, documentId) != 1) {
                throw new IllegalArgumentException("삭제할 문서를 찾을 수 없습니다.");
            }
            return;
        }

        if (jdbc.update("DELETE FROM document WHERE id=?", documentId) != 1) {
            throw new IllegalArgumentException("삭제할 문서를 찾을 수 없습니다.");
        }
    }

    private static boolean isConnectorSource(String sourceType) {
        return "GITHUB".equalsIgnoreCase(sourceType)
                || "GOOGLE_DRIVE".equalsIgnoreCase(sourceType)
                || "SLACK".equalsIgnoreCase(sourceType)
                || "NOTION".equalsIgnoreCase(sourceType);
    }

    private static final String ARCHIVED_CONTENT_PLACEHOLDER = "[보관 기간이 지나 본문이 정리되었습니다]";

    /**
     * Retention cleanup only: clears the heavy text/embedding content of versions belonging to documents
     * archived long enough ago. The document/document_version/document_chunk ROWS themselves are kept -
     * evidence/decision/todo/change records cite them by id - only full_text/content/embedding_json are
     * cleared. Every search/RAG query already excludes archived=TRUE documents, so this has no effect
     * beyond reclaiming space. Idempotent: already-cleared versions are skipped on later runs.
     */
    public int purgeArchivedContentOlderThan(java.time.LocalDate cutoff) {
        java.sql.Date cutoffDate = java.sql.Date.valueOf(cutoff);
        jdbc.update(
                """
                UPDATE document_chunk SET content=?,embedding_json=NULL
                WHERE version_id IN (
                  SELECT v.id FROM document_version v JOIN document d ON d.id=v.document_id
                  WHERE d.archived=TRUE AND d.archived_at<? AND v.full_text<>?
                )
                """,
                ARCHIVED_CONTENT_PLACEHOLDER, cutoffDate, ARCHIVED_CONTENT_PLACEHOLDER);
        return jdbc.update(
                """
                UPDATE document_version SET full_text=?
                WHERE full_text<>? AND document_id IN (
                  SELECT id FROM document WHERE archived=TRUE AND archived_at<?
                )
                """,
                ARCHIVED_CONTENT_PLACEHOLDER, ARCHIVED_CONTENT_PLACEHOLDER, cutoffDate);
    }

    public String versionText(long versionId) {
        return jdbc.queryForObject("SELECT full_text FROM document_version WHERE id=?", String.class, versionId);
    }


    public Map<String,Object> chunkDetail(long chunkId) {
        return jdbc.queryForMap("""
                SELECT c.id chunk_id,c.version_id,c.paragraph_ref,c.page_no,c.content,
                       d.id document_id,d.project_id,d.original_name,d.source_type,
                       v.version_no,v.summary,v.full_text
                FROM document_chunk c
                JOIN document_version v ON v.id=c.version_id
                JOIN document d ON d.id=v.document_id
                WHERE c.id=?
                """, chunkId);
    }

    public Map<String,Object> versionDetail(long versionId) {
        return jdbc.queryForMap("""
                SELECT v.id version_id,v.version_no,v.sha256,v.parse_status,v.summary,v.full_text,v.created_at,
                       d.id document_id,d.project_id,d.original_name,d.source_type,d.source_identifier,d.source_deleted,d.archived
                FROM document_version v
                JOIN document d ON d.id=v.document_id
                WHERE v.id=?
                """, versionId);
    }

    public long documentIdForVersion(long versionId) {
        return jdbc.queryForObject("SELECT document_id FROM document_version WHERE id=?", Long.class, versionId);
    }

    public long projectIdForVersion(long versionId) {
        return jdbc.queryForObject(
                """
                SELECT d.project_id
                FROM document_version v
                JOIN document d ON d.id=v.document_id
                WHERE v.id=?
                """,
                Long.class,
                versionId
        );
    }

    public void updateSummary(long versionId, String summary) {
        jdbc.update("UPDATE document_version SET summary=? WHERE id=?", summary, versionId);
    }

    /** Search only Hub-native sources so connector imports are not duplicated in unified material search. */
    public List<SearchHit> searchNative(long projectId, String query, int limit) {
        return searchByContent(projectId, query, limit);
    }


    /**
     * Lexical side of hybrid retrieval. Search title/file name, paragraph location and content,
     * but only from the latest immutable version of each non-archived/non-deleted document.
     */
    private List<SearchHit> searchByContent(long projectId, String query, int limit) {
        List<String> terms = SearchText.terms(query);
        if (terms.isEmpty()) return List.of();

        StringBuilder matches = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) matches.append(" OR ");
            matches.append("(lower(COALESCE(d.original_name,'')) LIKE ? OR lower(COALESCE(c.paragraph_ref,'')) LIKE ? OR lower(c.content) LIKE ?)");
            String like = "%" + SearchText.lower(terms.get(i)) + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        String sourceFilter = " AND d.source_type NOT IN ('GITHUB','GOOGLE_DRIVE','SLACK','NOTION')";
        String full = SearchText.lower(terms.get(0));
        args.add(full);
        args.add("%" + full + "%");
        args.add("%" + full + "%");
        args.add("%" + full + "%");
        args.add(Math.max(1, Math.min(limit, 200)));

        String sql = """
                SELECT c.id chunk_id,c.version_id,d.id document_id,v.version_no,d.source_type,d.source_identifier,
                       d.original_name,c.paragraph_ref,c.content,u.display_name author,v.created_at source_created_at
                FROM document_chunk c
                JOIN document_version v ON v.id=c.version_id
                JOIN document d ON d.id=v.document_id
                JOIN app_user u ON u.id=d.created_by
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                """ + sourceFilter + " AND (" + matches + ") " + """
                ORDER BY
                  CASE
                    WHEN lower(COALESCE(d.original_name,'')) = ? THEN 0
                    WHEN lower(COALESCE(d.original_name,'')) LIKE ? THEN 1
                    WHEN lower(COALESCE(c.paragraph_ref,'')) LIKE ? THEN 2
                    WHEN lower(c.content) LIKE ? THEN 3
                    ELSE 4
                  END,
                  v.created_at DESC,
                  c.chunk_index ASC
                LIMIT ?
                """;
        return jdbc.query(sql, this::mapSearchHit, args.toArray());
    }

    public List<DocumentVersionRef> latestByExactName(long projectId, String filename, int limit) {
        if (filename == null || filename.isBlank()) return List.of();
        return jdbc.query(
                """
                SELECT d.id document_id,v.id version_id,v.version_no,d.original_name,d.source_type,d.source_identifier,v.full_text
                FROM document d
                JOIN document_version v ON v.document_id=d.id
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND d.source_type NOT IN ('GITHUB','GOOGLE_DRIVE','SLACK','NOTION')
                  AND lower(COALESCE(d.original_name,''))=lower(?)
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                ORDER BY v.created_at DESC,d.id DESC LIMIT ?
                """,
                (rs, n) -> new DocumentVersionRef(
                        rs.getLong("document_id"), rs.getLong("version_id"), rs.getInt("version_no"),
                        rs.getString("original_name"), rs.getString("source_type"), rs.getString("source_identifier"),
                        rs.getString("full_text")
                ), projectId, filename.trim(), Math.max(1, Math.min(limit, 50)));
    }

    /** Latest active Hub-native documents whose filenames match configured glob patterns. */
    public List<DocumentVersionRef> latestByNamePatterns(long projectId, List<String> patterns, int limit) {
        if (patterns == null || patterns.isEmpty()) return List.of();
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            if (!where.isEmpty()) where.append(" OR ");
            where.append("lower(COALESCE(d.original_name,'')) LIKE ? ESCAPE '!'");
            args.add(SearchText.globLike(pattern));
        }
        if (where.isEmpty()) return List.of();
        args.add(Math.max(1, Math.min(limit, 20)));
        String sql = """
                SELECT d.id document_id,v.id version_id,v.version_no,d.original_name,d.source_type,d.source_identifier,v.full_text
                FROM document d
                JOIN document_version v ON v.document_id=d.id
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND d.source_type NOT IN ('GITHUB','GOOGLE_DRIVE','SLACK','NOTION')
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                  AND (""" + where + ") ORDER BY v.created_at DESC,d.id DESC LIMIT ?";
        return jdbc.query(sql, (rs, n) -> new DocumentVersionRef(
                rs.getLong("document_id"), rs.getLong("version_id"), rs.getInt("version_no"),
                rs.getString("original_name"), rs.getString("source_type"), rs.getString("source_identifier"),
                rs.getString("full_text")
        ), args.toArray());
    }

    /** High-confidence metadata candidates used by the query router before final reranking. */
    public List<SearchHit> searchNativeByMetadata(long projectId,
                                                  String author,
                                                  OffsetDateTime fromInclusive,
                                                  OffsetDateTime toExclusive,
                                                  java.util.Set<String> sourceTypes,
                                                  int limit) {
        boolean hasAuthor = author != null && !author.isBlank();
        boolean hasFrom = fromInclusive != null;
        boolean hasTo = toExclusive != null;
        java.util.Set<String> nativeTypes = sourceTypes == null ? java.util.Set.of() : sourceTypes.stream()
                .filter(type -> !java.util.Set.of("GITHUB", "GOOGLE_DRIVE", "SLACK", "NOTION").contains(type))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!hasAuthor && !hasFrom && !hasTo && nativeTypes.isEmpty()) return List.of();
        if (sourceTypes != null && !sourceTypes.isEmpty() && nativeTypes.isEmpty()) return List.of();

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (hasAuthor) {
            where.append(" AND lower(u.display_name) LIKE ?");
            args.add("%" + SearchText.lower(author) + "%");
        }
        if (hasFrom) { where.append(" AND v.created_at>=?"); args.add(Timestamp.from(fromInclusive.toInstant())); }
        if (hasTo) { where.append(" AND v.created_at<?"); args.add(Timestamp.from(toExclusive.toInstant())); }
        if (!nativeTypes.isEmpty()) {
            where.append(" AND d.source_type IN (");
            int i = 0;
            for (String type : nativeTypes) {
                if (i++ > 0) where.append(',');
                where.append('?');
                args.add(type);
            }
            where.append(')');
        }
        args.add(Math.max(1, Math.min(limit, 200)));
        String sql = """
                SELECT c.id chunk_id,c.version_id,d.id document_id,v.version_no,d.source_type,d.source_identifier,
                       d.original_name,c.paragraph_ref,c.content,u.display_name author,v.created_at source_created_at
                FROM document_chunk c
                JOIN document_version v ON v.id=c.version_id
                JOIN document d ON d.id=v.document_id
                JOIN app_user u ON u.id=d.created_by
                WHERE d.project_id=? AND d.archived=FALSE AND d.source_deleted=FALSE
                  AND d.source_type NOT IN ('GITHUB','GOOGLE_DRIVE','SLACK','NOTION')
                  AND v.version_no=(SELECT MAX(v2.version_no) FROM document_version v2 WHERE v2.document_id=d.id)
                """ + where + " ORDER BY v.created_at DESC,c.chunk_index ASC LIMIT ?";
        return jdbc.query(sql, this::mapSearchHit, args.toArray());
    }

    public List<SearchHit> chunksForVersion(long versionId) {
        return jdbc.query(
                """
                SELECT c.id chunk_id,c.version_id,d.id document_id,v.version_no,d.source_type,d.source_identifier,
                       d.original_name,c.paragraph_ref,c.content,u.display_name author,v.created_at source_created_at
                FROM document_chunk c
                JOIN document_version v ON v.id=c.version_id
                JOIN document d ON d.id=v.document_id
                JOIN app_user u ON u.id=d.created_by
                WHERE c.version_id=?
                ORDER BY c.chunk_index
                """,
                this::mapSearchHit,
                versionId
        );
    }

    private SearchHit mapSearchHit(ResultSet rs, int rowNum) throws SQLException {
        return new SearchHit(
                rs.getLong("chunk_id"),
                rs.getLong("version_id"),
                rs.getLong("document_id"),
                rs.getInt("version_no"),
                rs.getString("source_type"),
                rs.getString("source_identifier"),
                rs.getString("original_name"),
                rs.getString("paragraph_ref"),
                rs.getString("content"),
                rs.getString("author"),
                offsetDateTime(rs.getTimestamp("source_created_at"))
        );
    }


    private static OffsetDateTime offsetDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }

}