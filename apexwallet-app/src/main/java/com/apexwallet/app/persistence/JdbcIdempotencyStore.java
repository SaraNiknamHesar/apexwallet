package com.apexwallet.app.persistence;

import com.apexwallet.app.idempotency.IdempotencyRecord;
import com.apexwallet.app.idempotency.IdempotencyState;
import com.apexwallet.app.idempotency.IdempotencyStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@Profile("postgres")
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final RowMapper<IdempotencyRecord> ROW = (row, ignored) -> new IdempotencyRecord(
            row.getString("owner_ref"),
            row.getString("idempotency_key"),
            row.getString("request_hash").trim(),
            row.getString("response_body"),
            row.getObject("response_status", Integer.class),
            IdempotencyState.valueOf(row.getString("state")),
            row.getString("failure_reason"),
            SqlTimes.instant(row, "created_at"),
            SqlTimes.instant(row, "expires_at"));

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> tryInsert(IdempotencyRecord record) {
        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys
                    (owner_ref, idempotency_key, request_hash, state, response_status, response_body, expires_at, failure_reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_ref, idempotency_key) DO NOTHING
                """, record.owner(), record.key(), record.requestHash(), record.state().name(), record.statusCode(),
                record.responseBody(), SqlTimes.timestamp(record.expiresAt()), record.failureReason(),
                SqlTimes.timestamp(record.createdAt()));
        if (inserted == 1) {
            return Optional.empty();
        }
        return find(record.owner(), record.key());
    }

    @Override
    public Optional<IdempotencyRecord> find(String owner, String key) {
        return jdbc.query("""
                select owner_ref, idempotency_key, request_hash, state, response_status, response_body,
                       expires_at, failure_reason, created_at
                from idempotency_keys where owner_ref = ? and idempotency_key = ?
                """, ROW, owner, key).stream().findFirst();
    }

    @Override
    public void complete(String owner, String key, int statusCode, String responseBody) {
        jdbc.update("""
                update idempotency_keys
                set state = 'COMPLETED', response_status = ?, response_body = ?, failure_reason = null
                where owner_ref = ? and idempotency_key = ?
                """, statusCode, responseBody, owner, key);
    }

    @Override
    public void fail(String owner, String key, String reason) {
        jdbc.update("""
                update idempotency_keys
                set state = 'FAILED', response_status = 409, failure_reason = ?
                where owner_ref = ? and idempotency_key = ?
                """, reason, owner, key);
    }

    @Override
    public boolean deleteIfExpired(String owner, String key, Instant now) {
        return jdbc.update("""
                delete from idempotency_keys
                where owner_ref = ? and idempotency_key = ? and expires_at <= ?
                """, owner, key, SqlTimes.timestamp(now)) == 1;
    }

    @Override
    public int deleteExpired(Instant now) {
        return jdbc.update("delete from idempotency_keys where expires_at <= ?", SqlTimes.timestamp(now));
    }
}
