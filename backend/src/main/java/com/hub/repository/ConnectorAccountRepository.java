package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Connector credentials belong to the account that granted them and must survive a restart, so they
 * live here rather than in a map that empties every time the server comes back up.
 *
 * The credential is looked up by (user_id, connector_type): each person's own Google/Slack/GitHub/
 * Notion account can see different files/channels/repos than a teammate's, so linking is personal, not
 * shared project-wide. What gets imported still lands in the current project's shared document pool -
 * combining happens at the data level, not by sharing one login. project_id is recorded only for
 * reference (which project the link was made from); it is never part of the lookup key.
 */
@Repository
public class ConnectorAccountRepository {
    private final JdbcTemplate jdbc;

    public ConnectorAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Credential(String accessToken, String refreshToken, Instant expiresAt, String accountLabel) {}

    public void save(long userId, Long projectId, String type, String accessToken, String refreshToken, Instant expiresAt) {
        save(userId, projectId, type, accessToken, refreshToken, expiresAt, null);
    }

    /** accountLabel names the external account that granted access, so the screen can show which one is linked. */
    public void save(long userId, Long projectId, String type, String accessToken, String refreshToken, Instant expiresAt, String accountLabel) {
        int updated = jdbc.update("""
                UPDATE connector_account SET project_id=COALESCE(?,project_id),access_token_enc=?,refresh_token_enc=COALESCE(?,refresh_token_enc),
                    expires_at=?,external_account_id=COALESCE(?,external_account_id),status='CONNECTED',updated_at=now()
                WHERE user_id=? AND connector_type=?
                """, projectId, accessToken, refreshToken, expiresAt == null ? null : Timestamp.from(expiresAt), accountLabel, userId, type);
        if (updated > 0) return;
        jdbc.update("""
                INSERT INTO connector_account(user_id,project_id,connector_type,status,access_token_enc,refresh_token_enc,expires_at,external_account_id)
                VALUES(?,?,?,'CONNECTED',?,?,?,?)
                """, userId, projectId, type, accessToken, refreshToken, expiresAt == null ? null : Timestamp.from(expiresAt), accountLabel);
    }

    public Optional<Credential> find(long userId, String type) {
        return jdbc.query("""
                SELECT access_token_enc,refresh_token_enc,expires_at,external_account_id FROM connector_account
                WHERE user_id=? AND connector_type=? AND status='CONNECTED'
                """, (rs, n) -> {
            Timestamp expires = rs.getTimestamp("expires_at");
            return new Credential(rs.getString("access_token_enc"), rs.getString("refresh_token_enc"),
                    expires == null ? null : expires.toInstant(), rs.getString("external_account_id"));
        }, userId, type).stream().findFirst();
    }

    public Long id(long userId, String type) {
        return jdbc.query("""
                SELECT id FROM connector_account
                WHERE user_id=? AND connector_type=? AND status='CONNECTED'
                """, (rs, n) -> rs.getLong("id"), userId, type).stream().findFirst().orElse(null);
    }

    public boolean connected(long userId, String type) { return find(userId, type).isPresent(); }

    /** Most recently active linked account for this project+connector - who the auto-sync job replays a scope as. */
    public Optional<Long> anyConnectedUserId(long projectId, String type) {
        return jdbc.query("""
                SELECT user_id FROM connector_account
                WHERE project_id=? AND connector_type=? AND status='CONNECTED'
                ORDER BY updated_at DESC LIMIT 1
                """, (rs, n) -> rs.getLong("user_id"), projectId, type).stream().findFirst();
    }

    public String accountLabel(long userId, String type) {
        return find(userId, type).map(Credential::accountLabel).orElse(null);
    }

    public void disconnect(long userId, String type) {
        jdbc.update("DELETE FROM connector_account WHERE user_id=? AND connector_type=?", userId, type);
    }
}
