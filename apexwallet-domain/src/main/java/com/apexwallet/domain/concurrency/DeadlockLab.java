package com.apexwallet.domain.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Teaching stand-in: locking in caller order can deadlock. Production code uses {@link OrderedLockExecutor}.
 */
public final class DeadlockLab {

    private final ReentrantLock first = new ReentrantLock();
    private final ReentrantLock second = new ReentrantLock();

    public void acquire(boolean lowToHigh, CountDownLatch holdingFirst, CountDownLatch release) throws InterruptedException {
        ReentrantLock a = lowToHigh ? first : second;
        ReentrantLock b = lowToHigh ? second : first;
        a.lockInterruptibly();
        try {
            holdingFirst.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) {
                return;
            }
            b.lockInterruptibly();
            try {
                Thread.sleep(1);
            } finally {
                b.unlock();
            }
        } finally {
            if (a.isHeldByCurrentThread()) {
                a.unlock();
            }
        }
    }
}
