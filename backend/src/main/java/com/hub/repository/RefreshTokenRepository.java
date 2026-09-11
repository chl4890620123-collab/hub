package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Refresh tokens are opaque random values. Only SHA-256 hashes are stored in the database.
 * Rotated tokens remain until their original expiry so a replayed old token can be detected.
 */
@Repository
public class RefreshTokenRepository {
    private final JdbcTemplate jdbc;

    public RefreshTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TokenState(
            long id,
            long userId,
            String familyId,
            Instant expiresAt,
            Instant revokedAt,
            String revokedReason
    ) {
        public boolean expired(Instant now) {
            return expiresAt == null || !expiresAt.isAfter(now);
        }

        public boolean revoked() {
            return revokedAt != null;
        }
    }

    public Optional<TokenState> find(String tokenHash) {
        List<TokenState> rows = jdbc.query(
                """
                SELECT id,user_id,family_id,expires_at,revoked_at,revoked_reason
                FROM refresh_token
                WHERE token_hash=?
                """,
                (rs, n) -> {
                    Timestamp revoked = rs.getTimestamp("revoked_at");
                    return new TokenState(
                            rs.getLong("id"),
                            rs.getLong("user_id"),
                            rs.getString("family_id"),
                            rs.getTimestamp("expires_at").toInstant(),
                            revoked == null ? null : revoked.toInstant(),
                            rs.getString("revoked_reason")
                    );
                },
                tokenHash
        );
        return rows.stream().findFirst();
    }

    public void create(long userId, String familyId, String tokenHash, Instant expiresAt,
                       String userAgent, String ipAddress) {
        jdbc.update(
                """
                INSERT INTO refresh_token(user_id,family_id,token_hash,expires_at,user_agent,ip_address)
                VALUES(?,?,?,?,?,?)
                """,
                userId,
                trim(familyId, 64),
                tokenHash,
                Timestamp.from(expiresAt),
                trim(userAgent, 500),
                trim(ipAddress, 80)
        );
    }

    /**
     * Atomically consumes one refresh token for rotation. The conditional UPDATE is the
     * concurrency boundary: only one request can change an unrevoked token to ROTATED.
     */
    public boolean consumeForRotation(String tokenHash) {
        int updated = jdbc.update(
                """
                UPDATE refresh_token
                SET revoked_at=CURRENT_TIMESTAMP, revoked_reason='ROTATED'
                WHERE token_hash=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP
                """,
                tokenHash
        );
        return updated == 1;
    }

    public void revokeFamily(String familyId, String reason) {
        if (familyId == null || familyId.isBlank()) return;
        jdbc.update(
                """
                UPDATE refresh_token
                SET revoked_at=CURRENT_TIMESTAMP, revoked_reason=?
                WHERE family_id=? AND revoked_at IS NULL
                """,
                trim(reason, 100), familyId
        );
    }

    public void revokeAllForUser(long userId, String reason) {
        jdbc.update(
                """
                UPDATE refresh_token
                SET revoked_at=CURRENT_TIMESTAMP, revoked_reason=?
                WHERE user_id=? AND revoked_at IS NULL
                """,
                trim(reason, 100), userId
        );
    }


    public boolean isFamilyActive(String familyId) {
        if (familyId == null || familyId.isBlank()) return false;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE family_id=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP",
                Integer.class, familyId
        );
        return count != null && count > 0;
    }

    /** Old rotated tokens are retained until expiry to enable replay detection. */
    public void deleteExpired() {
        jdbc.update("DELETE FROM refresh_token WHERE expires_at<CURRENT_TIMESTAMP");
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
