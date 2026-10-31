package com.apexwallet.app.idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("local")
public class MemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, IdempotencyRecord> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> tryInsert(IdempotencyRecord record) {
        return Optional.ofNullable(rows.putIfAbsent(id(record.owner(), record.key()), record));
    }

    @Override
    public Optional<IdempotencyRecord> find(String owner, String key) {
        return Optional.ofNullable(rows.get(id(owner, key)));
    }

    @Override
    public void complete(String owner, String key, int statusCode, String responseBody) {
        rows.computeIfPresent(id(owner, key), (ignored, current) -> new IdempotencyRecord(
                current.owner(), current.key(), current.requestHash(), responseBody, statusCode,
                IdempotencyState.COMPLETED, null, current.createdAt(), current.expiresAt()));
    }

    @Override
    public void fail(String owner, String key, String reason) {
        rows.computeIfPresent(id(owner, key), (ignored, current) -> new IdempotencyRecord(
                current.owner(), current.key(), current.requestHash(), null, 409,
                IdempotencyState.FAILED, reason, current.createdAt(), current.expiresAt()));
    }

    @Override
    public boolean deleteIfExpired(String owner, String key, Instant now) {
        String rowId = id(owner, key);
        IdempotencyRecord current = rows.get(rowId);
        if (current != null && !current.expiresAt().isAfter(now)) {
            return rows.remove(rowId, current);
        }
        return false;
    }

    @Override
    public int deleteExpired(Instant now) {
        AtomicInteger removed = new AtomicInteger();
        rows.entrySet().removeIf(entry -> {
            if (!entry.getValue().expiresAt().isAfter(now)) {
                removed.incrementAndGet();
                return true;
            }
            return false;
        });
        return removed.get();
    }

    private static String id(String owner, String key) {
        return owner + "\n" + key;
    }
}
