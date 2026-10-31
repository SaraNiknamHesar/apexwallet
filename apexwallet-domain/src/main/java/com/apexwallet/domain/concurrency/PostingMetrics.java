package com.apexwallet.domain.concurrency;

import java.util.concurrent.atomic.LongAdder;

/** Counters and metrics may use a lock-free adder. A transfer of two accounts may not. */
public final class PostingMetrics {

    private final LongAdder posted = new LongAdder();

    public void recordPosted() {
        posted.increment();
    }

    public long posted() {
        return posted.sum();
    }
}
