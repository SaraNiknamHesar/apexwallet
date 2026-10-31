package com.apexwallet.app.persistence;

/** Thrown when the unique idempotency index loses a race. The caller loads the winning row. */
final class IdempotencyRace extends RuntimeException {

    final String key;

    IdempotencyRace(String key) {
        super("idempotency key already stored");
        this.key = key;
    }

    String key() {
        return key;
    }
}
