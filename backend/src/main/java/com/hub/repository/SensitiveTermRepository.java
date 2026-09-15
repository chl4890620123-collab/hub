package com.hub.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class SensitiveTermRepository {
    private final JdbcTemplate jdbc;
    public SensitiveTermRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record SensitiveTerm(long id, String term, long createdBy, OffsetDateTime createdAt) {}

    /** Longest-first so a phrase is redacted whole before a shorter term registered inside it. */
    public List<SensitiveTerm> list() {
        return jdbc.query("SELECT id,term,created_by,created_at FROM sensitive_term ORDER BY LENGTH(term) DESC, id DESC",
                (rs, n) -> new SensitiveTerm(
                        rs.getLong("id"), rs.getString("term"), rs.getLong("created_by"),
                        rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC)));
    }

    /** True when added; false when this exact term was already registered. */
    public boolean add(String term, long createdBy) {
        try {
            jdbc.update("INSERT INTO sensitive_term(term,created_by) VALUES(?,?)", term, createdBy);
            return true;
        } catch (DuplicateKeyException alreadyRegistered) {
            return false;
        }
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM sensitive_term WHERE id=?", id);
    }
}
