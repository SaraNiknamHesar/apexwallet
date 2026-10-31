package com.apexwallet.app.persistence;

import com.apexwallet.app.identity.AppUser;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.port.UserDirectory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@Profile("postgres")
public class JdbcUserDirectory implements UserDirectory {

    private final JdbcTemplate jdbc;

    public JdbcUserDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AppUser user) {
        jdbc.update("""
                INSERT INTO app_users (id, username, password_hash, role, failed_attempts, locked_until)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    username = excluded.username,
                    password_hash = excluded.password_hash,
                    role = excluded.role,
                    failed_attempts = excluded.failed_attempts,
                    locked_until = excluded.locked_until
                """, user.id(), user.username(), user.passwordHash(), user.role().name(), user.failedAttempts(),
                SqlTimes.timestamp(user.lockedUntil()));
    }

    @Override
    public Optional<AppUser> findByUsername(String username) {
        return one("select id, username, password_hash, role, failed_attempts, locked_until from app_users where username = ?", username);
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        return one("select id, username, password_hash, role, failed_attempts, locked_until from app_users where id = ?", id);
    }

    private Optional<AppUser> one(String sql, Object key) {
        return jdbc.query(sql, (row, ignored) -> new AppUser(
                row.getObject("id", UUID.class),
                row.getString("username"),
                row.getString("password_hash"),
                Role.valueOf(row.getString("role")),
                row.getInt("failed_attempts"),
                SqlTimes.instant(row, "locked_until")), key).stream().findFirst();
    }
}
