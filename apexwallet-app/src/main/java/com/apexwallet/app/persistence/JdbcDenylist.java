package com.apexwallet.app.persistence;

import com.apexwallet.app.security.Denylist;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("postgres")
public class JdbcDenylist implements Denylist {

    private final JdbcTemplate jdbc;

    public JdbcDenylist(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void revoke(String jti, Instant expiry) {
        jdbc.update("""
                INSERT INTO token_denylist (jti, expires_at) VALUES (?, ?)
                ON CONFLICT (jti) DO NOTHING
                """, jti, SqlTimes.timestamp(expiry));
    }

    @Override
    public boolean contains(String jti) {
        if (jti == null) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "select count(*) from token_denylist where jti = ?", Integer.class, jti);
        return count != null && count > 0;
    }
}
