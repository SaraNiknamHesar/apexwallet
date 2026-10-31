package com.apexwallet.domain.ledger;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) implements Comparable<TransactionId> {

    public TransactionId {
        Objects.requireNonNull(value, "value");
    }

    public static TransactionId random() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId parse(String text) {
        return new TransactionId(UUID.fromString(text));
    }

    @Override
    public int compareTo(TransactionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
