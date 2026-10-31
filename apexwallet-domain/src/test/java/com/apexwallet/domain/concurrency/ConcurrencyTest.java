package com.apexwallet.domain.concurrency;

import com.apexwallet.domain.account.AccountId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {

    @Test
    void lostUpdateDestroysTenUnits() throws Exception {
        LostUpdate box = new LostUpdate(100);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Thread first = new Thread(() -> run(box, barrier));
        Thread second = new Thread(() -> run(box, barrier));
        first.start();
        second.start();
        first.join();
        second.join();
        assertEquals(110, box.balance());
    }

    @Test
    void orderedLocksKeepTheSumAcrossTenThousandTransfers() throws Exception {
        AccountId[] ids = {AccountId.random(), AccountId.random(), AccountId.random(), AccountId.random()};
        Map<AccountId, Long> balances = new ConcurrentHashMap<>();
        for (AccountId id : ids) {
            balances.put(id, 1_000_000L);
        }
        OrderedLockExecutor locks = new OrderedLockExecutor();
        int transfers = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(transfers);
        long started = System.nanoTime();
        for (int i = 0; i < transfers; i++) {
            pool.submit(() -> {
                try {
                    int from = ThreadLocalRandom.current().nextInt(ids.length);
                    int to = ThreadLocalRandom.current().nextInt(ids.length);
                    if (from == to) {
                        return;
                    }
                    long amount = 1 + ThreadLocalRandom.current().nextInt(50);
                    locks.run(ids[from], ids[to], () -> {
                        long available = balances.get(ids[from]);
                        if (available < amount) {
                            return;
                        }
                        balances.put(ids[from], available - amount);
                        balances.put(ids[to], balances.get(ids[to]) + amount);
                    });
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        long sum = balances.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(4_000_000L, sum);
        assertTrue(elapsedMs < 30_000, "elapsedMs=" + elapsedMs);
    }

    @Test
    void oppositeLockOrderDeadlocksAndInterruptReleasesIt() throws Exception {
        DeadlockLab lab = new DeadlockLab();
        CountDownLatch holding = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Thread lowToHigh = named("low-to-high", () -> lab.acquire(true, holding, release));
        Thread highToLow = named("high-to-low", () -> lab.acquire(false, holding, release));
        lowToHigh.start();
        highToLow.start();
        assertTrue(holding.await(2, TimeUnit.SECONDS));
        release.countDown();
        Thread.sleep(300);
        assertTrue(isStuck(lowToHigh));
        assertTrue(isStuck(highToLow));
        lowToHigh.interrupt();
        highToLow.interrupt();
        lowToHigh.join(2_000);
        highToLow.join(2_000);
        assertFalse(lowToHigh.isAlive());
        assertFalse(highToLow.isAlive());
    }

    @Test
    void longAdderMatchesPostedCount() throws Exception {
        PostingMetrics metrics = new PostingMetrics();
        int threads = 100;
        int each = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                for (int n = 0; n < each; n++) {
                    metrics.recordPosted();
                }
                done.countDown();
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(threads * each, metrics.posted());
    }

    @Test
    void volatileWriteIsVisibleAfterTheWritingThreadFinishes() throws Exception {
        StopFlag flag = new StopFlag();
        Thread writer = new Thread(() -> flag.volatileStop = true);
        writer.start();
        writer.join();
        assertTrue(flag.volatileStop);
    }

    private static void run(LostUpdate box, CyclicBarrier barrier) {
        try {
            box.addAfterBothHaveRead(10, barrier);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Thread named(String name, Interruptible action) {
        return new Thread(() -> {
            try {
                action.run();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, name);
    }

    private static boolean isStuck(Thread thread) {
        Thread.State state = thread.getState();
        return state == Thread.State.WAITING || state == Thread.State.BLOCKED || state == Thread.State.TIMED_WAITING;
    }

    @FunctionalInterface
    private interface Interruptible {
        void run() throws InterruptedException;
    }
}
