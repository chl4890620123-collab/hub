package com.hub.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorRepositoryVisibilityTest {
    private JdbcTemplate jdbc;
    private ConnectorRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:connector-visible-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        repository = new ConnectorRepository(jdbc);

        jdbc.execute("""
                CREATE TABLE document(
                  id BIGINT PRIMARY KEY,
                  project_id BIGINT NOT NULL,
                  source_type VARCHAR(40),
                  source_identifier VARCHAR(1000),
                  archived BOOLEAN NOT NULL DEFAULT FALSE,
                  source_deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbc.execute("""
                CREATE TABLE external_item(
                  id BIGINT PRIMARY KEY,
                  project_id BIGINT NOT NULL,
                  connector_account_id BIGINT,
                  external_id VARCHAR(1000) NOT NULL,
                  item_type VARCHAR(60),
                  title VARCHAR(1000),
                  content CLOB,
                  author VARCHAR(500),
                  source_url VARCHAR(2000),
                  source_created_at TIMESTAMP,
                  raw_metadata CLOB,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void archivedConnectorDocumentHidesItsSnapshotFromEveryRetrievalPath() {
        jdbc.update("INSERT INTO external_item(id,project_id,external_id,item_type,title,content,author) VALUES(1,10,'GITHUB:repo:item-1','GIT_ISSUE','숨긴 자료','secret release plan','Alice')");
        jdbc.update("INSERT INTO external_item(id,project_id,external_id,item_type,title,content,author) VALUES(2,10,'GITHUB:repo:item-2','GIT_ISSUE','보이는 자료','secret public note','Alice')");
        jdbc.update("INSERT INTO document(id,project_id,source_type,source_identifier,archived,source_deleted) VALUES(101,10,'GITHUB','GITHUB:repo:item-1',TRUE,FALSE)");
        jdbc.update("INSERT INTO document(id,project_id,source_type,source_identifier,archived,source_deleted) VALUES(102,10,'GITHUB','GITHUB:repo:item-2',FALSE,FALSE)");

        assertEquals(List.of("GITHUB:repo:item-2"),
                repository.search(10, "secret", 20).stream().map(ConnectorRepository.ExternalSearchRow::externalId).toList());
        assertTrue(repository.searchByExactTitle(10, "숨긴 자료", 20).isEmpty());
        assertTrue(repository.searchByTitlePatterns(10, List.of("*숨긴*"), 20).isEmpty());
        assertEquals(List.of("GITHUB:repo:item-2"),
                repository.searchByMetadata(10, "Alice", null, null, Set.of("GITHUB"), 20).stream()
                        .map(ConnectorRepository.ExternalSearchRow::externalId).toList());
        assertTrue(repository.findByExternalId(10, "GITHUB:repo:item-1").isEmpty());
    }
}
