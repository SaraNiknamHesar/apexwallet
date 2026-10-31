package com.apexwallet.app.idempotency;

import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyStore store;
    private final Clock clock;
    private final ApexProperties properties;

    public IdempotencyService(IdempotencyStore store, Clock clock, ApexProperties properties) {
        this.store = store;
        this.clock = clock;
        this.properties = properties;
    }

    public IdempotencyResult execute(String owner, String key, String hash, Supplier<IdempotencyResult> action) {
        return execute(owner, key, hash, action, 0);
    }

    public int purgeExpired() {
        return store.deleteExpired(clock.instant());
    }

    private IdempotencyResult execute(String owner, String key, String hash, Supplier<IdempotencyResult> action, int attempt) {
        Instant now = clock.instant();
        IdempotencyRecord incoming = new IdempotencyRecord(
                owner, key, hash, null, null, IdempotencyState.IN_PROGRESS, null, now, now.plus(properties.getIdempotencyTtl()));
        Optional<IdempotencyRecord> raced = store.tryInsert(incoming);
        if (raced.isEmpty()) {
            try {
                IdempotencyResult result = action.get();
                store.complete(owner, key, result.status(), result.body());
                return new IdempotencyResult(result.status(), result.body(), false);
            } catch (RuntimeException ex) {
                store.fail(owner, key, ex.getMessage());
                throw ex;
            }
        }
        return resolveExisting(owner, key, hash, raced.get(), action, attempt);
    }

    private IdempotencyResult resolveExisting(String owner, String key, String hash, IdempotencyRecord existing,
                                               Supplier<IdempotencyResult> action, int attempt) {
        if (!existing.expiresAt().isAfter(clock.instant())) {
            if (attempt >= 1 || !store.deleteIfExpired(owner, key, clock.instant())) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT, "expired key could not be replaced");
            }
            return execute(owner, key, hash, action, attempt + 1);
        }
        if (!existing.requestHash().equals(hash)) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT, "same key was used with a different request");
        }
        if (existing.state() == IdempotencyState.COMPLETED) {
            return new IdempotencyResult(existing.statusCode(), existing.responseBody(), true);
        }
        if (existing.state() == IdempotencyState.FAILED) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "key is failed and stays under investigation: " + existing.failureReason());
        }
        for (int i = 0; i < 40; i++) {
            pause();
            IdempotencyRecord latest = store.find(owner, key)
                    .orElseThrow(() -> new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT, "key disappeared"));
            if (!latest.requestHash().equals(hash)) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT, "same key was used with a different request");
            }
            if (latest.state() == IdempotencyState.COMPLETED) {
                return new IdempotencyResult(latest.statusCode(), latest.responseBody(), true);
            }
            if (latest.state() == IdempotencyState.FAILED) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "key is failed and stays under investigation: " + latest.failureReason());
            }
        }
        throw new DomainException(ErrorCode.IDEMPOTENCY_IN_PROGRESS, "original request is still in progress");
    }

    private static void pause() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DomainException(ErrorCode.IDEMPOTENCY_IN_PROGRESS, "interrupted while waiting for the original request");
        }
    }
}
