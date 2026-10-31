package com.apexwallet.app.persistence;

import com.apexwallet.app.messaging.OutboxMessage;
import com.apexwallet.app.messaging.OutboxQueue;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Profile("postgres")
public class JdbcOutbox implements OutboxQueue {

    private final JdbcTemplate jdbc;

    public JdbcOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(OutboxMessage message) {
        jdbc.update("""
                INSERT INTO outbox (id, aggregate_id, event_type, payload, attempts, created_at)
                VALUES (?, ?, 'POSTED', ?, ?, ?)
                """, UUID.fromString(message.id()), UUID.fromString(message.accountKey()), message.payload(),
                message.attempts(), SqlTimes.timestamp(Instant.now()));
    }

    @Override
    public List<OutboxMessage> unpublished() {
        return jdbc.query("""
                select id, aggregate_id, payload, attempts
                from outbox
                where published_at is null and dead = false
                order by created_at
                """, (row, ignored) -> new OutboxMessage(
                row.getObject("id", UUID.class).toString(),
                row.getObject("aggregate_id", UUID.class).toString(),
                row.getString("payload"),
                row.getInt("attempts")));
    }

    @Override
    public void markPublished(String id) {
        jdbc.update("update outbox set published_at = ? where id = ?",
                SqlTimes.timestamp(Instant.now()), UUID.fromString(id));
    }

    @Override
    public int attempt(String id) {
        List<Integer> attempts = jdbc.query("""
                update outbox set attempts = attempts + 1
                where id = ? and published_at is null and dead = false
                returning attempts
                """, (row, ignored) -> row.getInt(1), UUID.fromString(id));
        if (attempts.isEmpty()) {
            throw new IllegalStateException("outbox row missing: " + id);
        }
        return attempts.get(0);
    }

    @Override
    public void deadLetter(String id) {
        jdbc.update("update outbox set dead = true where id = ?", UUID.fromString(id));
    }

    @Override
    public int deadCount() {
        Integer count = jdbc.queryForObject("select count(*) from outbox where dead = true", Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public boolean isPublished(String id) {
        Integer count = jdbc.queryForObject(
                "select count(*) from outbox where id = ? and published_at is not null", Integer.class, UUID.fromString(id));
        return count != null && count > 0;
    }
}
