package com.apexwallet.app.idempotency;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyStore {

    /** Empty means this caller inserted the row. A value means someone else already owns the key. */
    Optional<IdempotencyRecord> tryInsert(IdempotencyRecord record);

    Optional<IdempotencyRecord> find(String owner, String key);

    void complete(String owner, String key, int statusCode, String responseBody);

    void fail(String owner, String key, String reason);

    boolean deleteIfExpired(String owner, String key, Instant now);

    int deleteExpired(Instant now);
}
