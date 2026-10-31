package com.apexwallet.app.persistence;

import com.apexwallet.app.audit.AuditEvent;
import com.apexwallet.app.audit.AuditSink;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Profile("postgres")
public class JdbcAuditSink implements AuditSink {

    private final JdbcTemplate jdbc;

    public JdbcAuditSink(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String action, String actor, Instant at, String correlationId) {
        jdbc.update("""
                INSERT INTO audit_log (id, actor, action, at, detail) VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), actor, action, SqlTimes.timestamp(at), correlationId == null ? "" : correlationId);
    }

    @Override
    public List<AuditEvent> events() {
        return jdbc.query("""
                select action, actor, at, detail from audit_log order by at, id
                """, (row, ignored) -> new AuditEvent(
                row.getString("action"),
                row.getString("actor"),
                SqlTimes.instant(row, "at"),
                row.getString("detail")));
    }
}
