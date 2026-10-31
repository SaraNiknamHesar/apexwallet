package com.apexwallet.app.idempotency;

import java.time.Instant;

public record IdempotencyRecord(
        String owner,
        String key,
        String requestHash,
        String responseBody,
        Integer statusCode,
        IdempotencyState state,
        String failureReason,
        Instant createdAt,
        Instant expiresAt
) {
}
