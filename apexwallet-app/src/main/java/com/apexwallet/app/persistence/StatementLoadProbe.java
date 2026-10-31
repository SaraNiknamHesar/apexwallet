package com.apexwallet.app.persistence;

import java.util.concurrent.atomic.AtomicInteger;

/** Counts loads so a test can show an N+1 statement query versus one joined query. */
public final class StatementLoadProbe {

    private final AtomicInteger loads = new AtomicInteger();

    public <T> T load(T value) {
        loads.incrementAndGet();
        return value;
    }

    public int loads() {
        return loads.get();
    }
}
