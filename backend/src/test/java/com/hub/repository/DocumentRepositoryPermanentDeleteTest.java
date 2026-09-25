package com.hub.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentRepositoryPermanentDeleteTest {
    private JdbcTemplate jdbc;
    private DocumentRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:document-delete-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
                  archived_at TIMESTAMP
                )
                """);
        jdbc.execute("CREATE TABLE document_version(id BIGINT PRIMARY KEY, document_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE document_chunk(id BIGINT PRIMARY KEY, version_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE todo(id BIGINT PRIMARY KEY, source_document_version_id BIGINT)");
        jdbc.execute("CREATE TABLE decision_candidate(id BIGINT PRIMARY KEY, source_document_version_id BIGINT)");
        jdbc.execute("CREATE TABLE ai_run(id BIGINT PRIMARY KEY, document_version_id BIGINT)");
        jdbc.execute("CREATE TABLE change_analysis(id BIGINT PRIMARY KEY, before_version_id BIGINT, after_version_id BIGINT)");
        jdbc.execute("CREATE TABLE evidence(id BIGINT PRIMARY KEY, version_id BIGINT, chunk_id BIGINT)");
        jdbc.execute("CREATE TABLE external_item(id BIGINT PRIMARY KEY, project_id BIGINT, external_id VARCHAR(1000), imported_document_id BIGINT)");
    }

    @Test
    void permanentDeleteOfConnectorSourceKeepsOnlyTombstoneAndRemovesAllContent() {
        jdbc.update("""
                INSERT INTO document(id,project_id,source_type,source_identifier,original_name,storage_path)
                VALUES(1,10,'GITHUB','GITHUB:repo:item-1','issue.md','/tmp/issue.md')
                """);
        jdbc.update("INSERT INTO document_version(id,document_id) VALUES(101,1)");
        jdbc.update("INSERT INTO document_chunk(id,version_id) VALUES(201,101)");
        jdbc.update("INSERT INTO todo(id,source_document_version_id) VALUES(301,101)");
        jdbc.update("INSERT INTO decision_candidate(id,source_document_version_id) VALUES(401,101)");
        jdbc.update("INSERT INTO ai_run(id,document_version_id) VALUES(501,101)");
        jdbc.update("INSERT INTO change_analysis(id,before_version_id,after_version_id) VALUES(601,101,101)");
        jdbc.update("INSERT INTO evidence(id,version_id,chunk_id) VALUES(701,101,201)");

        jdbc.update("INSERT INTO external_item(id,project_id,external_id,imported_document_id) VALUES(801,10,'GITHUB:repo:item-1',NULL)");
        jdbc.update("INSERT INTO external_item(id,project_id,external_id,imported_document_id) VALUES(802,10,'GITHUB:repo:keep-me',NULL)");

        repository.deletePermanently(1L);

        assertEquals(1, countWhere("document", "id=1"));
        assertTrue(repository.isSourceDeleted(10L, "GITHUB", "GITHUB:repo:item-1"));
        assertTrue(jdbc.queryForObject("SELECT archived FROM document WHERE id=1", Boolean.class));
        assertNull(jdbc.queryForObject("SELECT storage_path FROM document WHERE id=1", String.class));
        assertEquals(0, count("document_version"));
        assertEquals(0, count("document_chunk"));
        assertEquals(0, countWhere("external_item", "id=801"));
        assertEquals(1, countWhere("external_item", "id=802"));
        assertNull(jdbc.queryForObject("SELECT source_document_version_id FROM todo WHERE id=301", Long.class));
        assertNull(jdbc.queryForObject("SELECT source_document_version_id FROM decision_candidate WHERE id=401", Long.class));
        assertNull(jdbc.queryForObject("SELECT document_version_id FROM ai_run WHERE id=501", Long.class));
        assertEquals(0, count("change_analysis"));
        assertEquals(0, count("evidence"));
    }

    @Test
    void permanentDeleteOfLocalSourcePhysicallyRemovesDocumentRow() {
        jdbc.update("""
                INSERT INTO document(id,project_id,source_type,source_identifier,original_name,storage_path)
                VALUES(2,10,'FILE','file:local.pdf','local.pdf','/tmp/local.pdf')
                """);
        jdbc.update("INSERT INTO document_version(id,document_id) VALUES(102,2)");
        jdbc.update("INSERT INTO document_chunk(id,version_id) VALUES(202,102)");

        repository.deletePermanently(2L);

        assertEquals(0, countWhere("document", "id=2"));
        assertEquals(0, countWhere("document_version", "document_id=2"));
        assertEquals(0, countWhere("document_chunk", "version_id=102"));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int countWhere(String table, String predicate) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Integer.class);
    }
}
