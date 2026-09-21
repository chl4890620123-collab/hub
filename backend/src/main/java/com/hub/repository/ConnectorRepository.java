package com.hub.repository;

import com.hub.util.SearchText;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ConnectorRepository {
    private final JdbcTemplate jdbc;

    public ConnectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores a read-only connector snapshot idempotently.
     * The external id is namespaced by connector type and is unique inside one project.
     */
    public void saveItem(long projectId,
                         String connectorType,
                         String externalId,
                         String itemType,
                         String title,
                         String content,
                         String author,
                         String sourceUrl,
                         OffsetDateTime createdAt,
                         String metadataJson) {
        saveItem(projectId, null, connectorType, externalId, itemType, title, content, author, sourceUrl, createdAt, metadataJson);
    }

    public void saveItem(long projectId,
                         Long connectorAccountId,
                         String connectorType,
                         String externalId,
                         String itemType,
                         String title,
                         String content,
                         String author,
                         String sourceUrl,
                         OffsetDateTime createdAt,
                         String metadataJson) {
        String id = connectorType + ":" + externalId;
        Object timestamp = createdAt == null ? null : Timestamp.from(createdAt.toInstant());

        int updated = updateExisting(projectId, connectorAccountId, id, itemType, title, content, author, sourceUrl, timestamp, metadataJson);
        if (updated > 0) return;

        try {
            jdbc.update(
                    """
                    INSERT INTO external_item(
                        project_id,connector_account_id,external_id,item_type,title,content,author,
                        source_url,source_created_at,raw_metadata
                    ) VALUES(?,?,?,?,?,?,?,?,?,?)
                    """,
                    projectId, connectorAccountId, id, itemType, title, content, author, sourceUrl, timestamp, metadataJson
            );
        } catch (DuplicateKeyException concurrentSync) {
            // Another sync may have inserted the same provider item between UPDATE and INSERT.
            updateExisting(projectId, connectorAccountId, id, itemType, title, content, author, sourceUrl, timestamp, metadataJson);
        }
    }

    private int updateExisting(long projectId,
                               Long connectorAccountId,
                               String externalId,
                               String itemType,
                               String title,
                               String content,
                               String author,
                               String sourceUrl,
                               Object createdAt,
                               String metadataJson) {
        String accountClause = connectorAccountId == null ? "connector_account_id IS NULL" : "connector_account_id=?";
        String sql = """
                UPDATE external_item
                SET item_type=?,title=?,content=?,author=?,source_url=?,source_created_at=?,raw_metadata=?
                WHERE project_id=? AND %s AND external_id=?
                """.formatted(accountClause);
        if (connectorAccountId == null) {
            return jdbc.update(sql, itemType, title, content, author, sourceUrl, createdAt, metadataJson, projectId, externalId);
        }
        return jdbc.update(sql, itemType, title, content, author, sourceUrl, createdAt, metadataJson, projectId, connectorAccountId, externalId);
    }
    public record ExternalSearchRow(
            long id,
            String externalId,
            String itemType,
            String title,
            String content,
            String author,
            String sourceUrl,
            OffsetDateTime sourceCreatedAt,
            String rawMetadata
    ) {}

    /**
     * Searches connector snapshots with token-level matching so natural-language questions do not
     * require an exact phrase match. SQL parameters are always bound; query text is never concatenated.
     */
    public List<ExternalSearchRow> search(long projectId, String query, int limit) {
        List<String> terms = SearchText.terms(query);
        if (terms.isEmpty()) return List.of();

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) where.append(" OR ");
            where.append("(lower(COALESCE(title,'')) LIKE ? OR lower(COALESCE(content,'')) LIKE ? OR lower(COALESCE(author,'')) LIKE ?)");
            String like = "%" + SearchText.lower(terms.get(i)) + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        args.add(Math.max(1, Math.min(limit, 200)));

        String sql = "SELECT id,external_id,item_type,title,content,author,source_url,source_created_at,raw_metadata " +
                "FROM external_item WHERE project_id=? AND (" + where + ") " +
                "ORDER BY source_created_at DESC, id DESC LIMIT ?";

        return jdbc.query(
                sql,
                (rs, n) -> mapExternalRow(rs),
                args.toArray()
        );
    }


    public List<ExternalSearchRow> searchByExactTitle(long projectId, String title, int limit) {
        if (title == null || title.isBlank()) return List.of();
        return jdbc.query(
                "SELECT id,external_id,item_type,title,content,author,source_url,source_created_at,raw_metadata " +
                        "FROM external_item WHERE project_id=? AND lower(COALESCE(title,''))=lower(?) ORDER BY source_created_at DESC,id DESC LIMIT ?",
                (rs, n) -> mapExternalRow(rs), projectId, title.trim(), Math.max(1, Math.min(limit, 50)));
    }

    public List<ExternalSearchRow> searchByTitlePatterns(long projectId, List<String> patterns, int limit) {
        if (patterns == null || patterns.isEmpty()) return List.of();
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            if (!where.isEmpty()) where.append(" OR ");
            where.append("lower(COALESCE(title,'')) LIKE ? ESCAPE '!'");
            args.add(SearchText.globLike(pattern));
        }
        if (where.isEmpty()) return List.of();
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbc.query(
                "SELECT id,external_id,item_type,title,content,author,source_url,source_created_at,raw_metadata " +
                        "FROM external_item WHERE project_id=? AND (" + where + ") ORDER BY source_created_at DESC,id DESC LIMIT ?",
                (rs, n) -> mapExternalRow(rs), args.toArray());
    }

    /** Metadata-only connector candidates used when the query explicitly names author/date/source. */
    public List<ExternalSearchRow> searchByMetadata(long projectId,
                                                    String author,
                                                    OffsetDateTime fromInclusive,
                                                    OffsetDateTime toExclusive,
                                                    java.util.Set<String> sourceTypes,
                                                    int limit) {
        boolean hasAuthor = author != null && !author.isBlank();
        boolean hasFrom = fromInclusive != null;
        boolean hasTo = toExclusive != null;
        java.util.Set<String> externalTypes = sourceTypes == null ? java.util.Set.of() : sourceTypes.stream()
                .filter(java.util.Set.of("GITHUB", "GOOGLE_DRIVE", "SLACK", "NOTION")::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!hasAuthor && !hasFrom && !hasTo && externalTypes.isEmpty()) return List.of();
        if (sourceTypes != null && !sourceTypes.isEmpty() && externalTypes.isEmpty()) return List.of();

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (hasAuthor) {
            where.append(" AND lower(COALESCE(author,'')) LIKE ?");
            args.add("%" + SearchText.lower(author) + "%");
        }
        if (hasFrom) { where.append(" AND source_created_at>=?"); args.add(Timestamp.from(fromInclusive.toInstant())); }
        if (hasTo) { where.append(" AND source_created_at<?"); args.add(Timestamp.from(toExclusive.toInstant())); }
        if (!externalTypes.isEmpty()) {
            where.append(" AND (");
            int i = 0;
            for (String type : externalTypes) {
                if (i++ > 0) where.append(" OR ");
                where.append("external_id LIKE ?");
                args.add(type + ":%");
            }
            where.append(')');
        }
        args.add(Math.max(1, Math.min(limit, 200)));
        String sql = "SELECT id,external_id,item_type,title,content,author,source_url,source_created_at,raw_metadata " +
                "FROM external_item WHERE project_id=?" + where + " ORDER BY source_created_at DESC,id DESC LIMIT ?";
        return jdbc.query(sql, (rs, n) -> mapExternalRow(rs), args.toArray());
    }

    public Optional<ExternalSearchRow> findByExternalId(long projectId, String externalId) {
        if (externalId == null || externalId.isBlank()) return Optional.empty();
        List<ExternalSearchRow> rows = jdbc.query(
                "SELECT id,external_id,item_type,title,content,author,source_url,source_created_at,raw_metadata " +
                        "FROM external_item WHERE project_id=? AND external_id=? LIMIT 1",
                (rs, n) -> mapExternalRow(rs),
                projectId, externalId
        );
        return rows.stream().findFirst();
    }

    private static ExternalSearchRow mapExternalRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        var timestamp = rs.getTimestamp("source_created_at");
        return new ExternalSearchRow(
                rs.getLong("id"),
                rs.getString("external_id"),
                rs.getString("item_type"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("author"),
                rs.getString("source_url"),
                timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC),
                rs.getString("raw_metadata")
        );
    }

    public record SyncState(String connectorType, String externalScope, OffsetDateTime lastSyncedAt, String lastStatus, String lastError, int lastImportedCount) {}

    public void saveSyncState(long projectId, String connectorType, String scope, String status, String error, int count) {
        Timestamp now = Timestamp.from(java.time.Instant.now());
        int updated = jdbc.update("UPDATE connector_sync_state SET last_synced_at=?,last_status=?,last_error=?,last_imported_count=? WHERE project_id=? AND connector_type=? AND external_scope=?",
                now, status, error, count, projectId, connectorType, scope);
        if (updated > 0) return;
        try {
            jdbc.update("INSERT INTO connector_sync_state(project_id,connector_type,external_scope,last_synced_at,last_status,last_error,last_imported_count) VALUES(?,?,?,?,?,?,?)",
                    projectId, connectorType, scope, now, status, error, count);
        } catch (DuplicateKeyException race) {
            jdbc.update("UPDATE connector_sync_state SET last_synced_at=?,last_status=?,last_error=?,last_imported_count=? WHERE project_id=? AND connector_type=? AND external_scope=?",
                    now, status, error, count, projectId, connectorType, scope);
        }
    }

    public record SyncScope(long projectId, String connectorType, String externalScope) {}

    /** Every (project, connector, scope) combination ever imported, across all projects - the auto-sync job's worklist. */
    public List<SyncScope> allKnownScopes() {
        return jdbc.query("SELECT DISTINCT project_id,connector_type,external_scope FROM connector_sync_state",
                (rs, n) -> new SyncScope(rs.getLong("project_id"), rs.getString("connector_type"), rs.getString("external_scope")));
    }

    public List<SyncState> listSyncStates(long projectId) {
        return jdbc.query("SELECT connector_type,external_scope,last_synced_at,last_status,last_error,last_imported_count FROM connector_sync_state WHERE project_id=? ORDER BY last_synced_at DESC",
                (rs,n) -> {
                    Timestamp ts=rs.getTimestamp("last_synced_at");
                    return new SyncState(rs.getString("connector_type"),rs.getString("external_scope"),
                            ts==null?null:ts.toInstant().atOffset(ZoneOffset.UTC),rs.getString("last_status"),rs.getString("last_error"),rs.getInt("last_imported_count"));
                }, projectId);
    }

    /**
     * Retention cleanup only: removes imported-item bookkeeping rows left orphaned by a disconnected
     * connector account (connector_account_id is SET NULL on disconnect). Nothing else references
     * external_item, and the document it may have imported is never touched.
     */
    public int purgeOrphanedItemsOlderThan(java.time.LocalDate cutoff) {
        return jdbc.update(
                "DELETE FROM external_item WHERE connector_account_id IS NULL AND created_at<?",
                java.sql.Date.valueOf(cutoff));
    }

}
