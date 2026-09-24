package com.hub.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentRepositoryRetentionRestoreTest {
    private static final String PURGED = "[보관 기간이 지나 본문이 정리되었습니다]";

    private JdbcTemplate jdbc;
    private DocumentRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:document-restore-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        repository = new DocumentRepository(jdbc);

        jdbc.execute("""
                CREATE TABLE document(
                  id BIGINT PRIMARY KEY,
                  project_id BIGINT NOT NULL,
                  source_type VARCHAR(40),
                  source_identifier VARCHAR(1000),
                  original_name VARCHAR(500),
                  storage_path VARCHAR(2000),
                  archived BOOLEAN NOT NULL DEFAULT FALSE,
                  source_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                  archived_at TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE document_version(
                  id BIGINT PRIMARY KEY,
                  document_id BIGINT NOT NULL,
                  version_no INT NOT NULL,
                  sha256 VARCHAR(128),
                  full_text CLOB,
                  parse_status VARCHAR(30),
                  summary CLOB,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void restoreRejectsDocumentWhoseLatestBodyWasPurged() {
        insertDocument(1L, true);
        jdbc.update("INSERT INTO document_version(id,document_id,version_no,sha256,full_text,parse_status) VALUES(11,1,1,'same-hash',?,'READY')", PURGED);

        assertThrows(IllegalArgumentException.class, () -> repository.restore(1L));

        assertTrue(jdbc.queryForObject("SELECT archived FROM document WHERE id=1", Boolean.class));
        assertTrue(repository.isVersionContentPurged(11L));
        Map<String,Object> row = repository.listDocuments(10L).get(0);
        Object flag = row.get("content_purged");
        if (flag == null) flag = row.get("CONTENT_PURGED");
        assertTrue(Boolean.parseBoolean(String.valueOf(flag)));
    }

    @Test
    void activeNonPurgedVersionStillDeduplicatesNormally() {
        insertDocument(2L, false);
        jdbc.update("INSERT INTO document_version(id,document_id,version_no,sha256,full_text,parse_status) VALUES(21,2,1,'same-hash','real text','READY')");

        assertTrue(repository.findVersionByHash(10L, "FILE", "same-hash").isPresent());
        assertFalse(repository.isVersionContentPurged(21L));
    }

    @Test
    void archivedVersionIsNotUsedAsAnActiveUploadDuplicate() {
        insertDocument(3L, true);
        jdbc.update("INSERT INTO document_version(id,document_id,version_no,sha256,full_text,parse_status) VALUES(31,3,1,'archived-hash','real text','READY')");

        assertTrue(repository.findVersionByHash(10L, "FILE", "archived-hash").isEmpty());
    }

    private void insertDocument(long id, boolean archived) {
        jdbc.update("""
                INSERT INTO document(id,project_id,source_type,source_identifier,original_name,storage_path,archived,source_deleted,archived_at)
                VALUES(?,10,'FILE',?,'doc.pdf','/tmp/doc.pdf',?,FALSE,CURRENT_TIMESTAMP)
                """, id, "file:doc-" + id, archived);
    }
}
