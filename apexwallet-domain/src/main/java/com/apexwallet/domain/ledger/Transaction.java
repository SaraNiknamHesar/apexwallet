package com.apexwallet.domain.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Transaction(
        TransactionId id,
        TransactionType type,
        TransactionStatus status,
        List<LedgerEntry> entries,
        String description,
        TransactionId reverses,
        String idempotencyKey,
        Instant requestedAt,
        Instant postedAt
) {
    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        entries = List.copyOf(entries);
        description = description == null ? "" : description;
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    public Transaction withStatus(TransactionStatus next) {
        return new Transaction(id, type, next, entries, description, reverses, idempotencyKey, requestedAt, postedAt);
    }
}
