package com.apexwallet.app.persistence;

import com.apexwallet.app.security.RefreshSession;
import com.apexwallet.app.security.RefreshTokenStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The {@code username} column stores the user id. {@link RefreshSession} carries that id, not a login name.
 */
@Component
@Profile("postgres")
public class JdbcRefreshTokenStore implements RefreshTokenStore {

    private final JdbcTemplate jdbc;

    public JdbcRefreshTokenStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(RefreshSession session) {
        jdbc.update("""
                INSERT INTO refresh_sessions (token_hash, family_id, username, expires_at, revoked)
                VALUES (?, ?, ?, ?, ?)
                """, session.tokenHash(), session.familyId(), session.userId().toString(),
                SqlTimes.timestamp(session.expiresAt()), session.revoked());
    }

    @Override
    public Optional<RefreshSession> findByHash(String tokenHash) {
        return jdbc.query("""
                select token_hash, family_id, username, revoked, expires_at
                from refresh_sessions where token_hash = ?
                """, (row, ignored) -> new RefreshSession(
                row.getString("token_hash").trim(),
                row.getObject("family_id", UUID.class),
                UUID.fromString(row.getString("username").trim()),
                row.getBoolean("revoked"),
                SqlTimes.instant(row, "expires_at")), tokenHash).stream().findFirst();
    }

    @Override
    public void revoke(String tokenHash) {
        jdbc.update("update refresh_sessions set revoked = true where token_hash = ?", tokenHash);
    }

    @Override
    public void revokeFamily(UUID familyId) {
        jdbc.update("update refresh_sessions set revoked = true where family_id = ?", familyId);
    }
}
