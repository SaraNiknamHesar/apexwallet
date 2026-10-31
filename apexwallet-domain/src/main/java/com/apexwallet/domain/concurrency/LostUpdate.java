package com.apexwallet.domain.concurrency;

import java.util.concurrent.CyclicBarrier;

/**
 * Forces the lost-update interleaving: both threads read the same balance, then both write.
 * The barrier makes the bug deterministic so the test does not depend on luck.
 */
public final class LostUpdate {

    private long balance;

    public LostUpdate(long balance) {
        this.balance = balance;
    }

    public void addAfterBothHaveRead(long delta, CyclicBarrier barrier) throws Exception {
        long seen = balance;
        barrier.await();
        balance = seen + delta;
    }

    public long balance() {
        return balance;
    }
}
