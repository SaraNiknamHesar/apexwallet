package com.apexwallet.app.idempotency;

import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyServiceTest {

    @Test
    void fourCasesAndAFailedKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
        IdempotencyService service = service(clock, Duration.ofMinutes(10));
        AtomicInteger calls = new AtomicInteger();

        IdempotencyResult first = service.execute("alice", "k1", "hash-a", () -> {
            calls.incrementAndGet();
            return new IdempotencyResult(201, "{\"ok\":1}", false);
        });
        IdempotencyResult replay = service.execute("alice", "k1", "hash-a", () -> {
            calls.incrementAndGet();
            return new IdempotencyResult(201, "{\"ok\":2}", false);
        });
        assertEquals(1, calls.get());
        assertFalse(first.replay());
        assertTrue(replay.replay());
        assertEquals("{\"ok\":1}", replay.body());

        DomainException conflict = assertThrows(DomainException.class,
                () -> service.execute("alice", "k1", "hash-b", () -> new IdempotencyResult(201, "{}", false)));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, conflict.code());

        AtomicInteger failures = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> service.execute("alice", "k2", "hash-a", () -> {
            failures.incrementAndGet();
            throw new IllegalStateException("posting failed halfway");
        }));
        DomainException blocked = assertThrows(DomainException.class,
                () -> service.execute("alice", "k2", "hash-a", () -> {
                    failures.incrementAndGet();
                    return new IdempotencyResult(201, "{}", false);
                }));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, blocked.code());
        assertEquals(1, failures.get());

        service.execute("alice", "k3", "hash-a", () -> new IdempotencyResult(201, "old", false));
        clock.instant = clock.instant.plus(Duration.ofMinutes(11));
        IdempotencyResult renewed = service.execute("alice", "k3", "hash-a", () -> new IdempotencyResult(201, "new", false));
        assertEquals("new", renewed.body());
        assertFalse(renewed.replay());
        assertEquals(2, service.purgeExpired());
    }

    @Test
    void fiftyIdenticalRequestsPostOnce() throws Exception {
        IdempotencyService service = service(Clock.systemUTC(), Duration.ofHours(1));
        AtomicInteger posts = new AtomicInteger();
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger replays = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    IdempotencyResult result = service.execute("alice", "same", "hash", () -> {
                        posts.incrementAndGet();
                        return new IdempotencyResult(201, "body", false);
                    });
                    if (result.replay()) {
                        replays.incrementAndGet();
                    }
                    assertEquals("body", result.body());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
        go.countDown();
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, posts.get());
        assertEquals(49, replays.get());
    }

    private static IdempotencyService service(Clock clock, Duration ttl) {
        ApexProperties properties = new ApexProperties();
        properties.setIdempotencyTtl(ttl);
        return new IdempotencyService(new MemoryIdempotencyStore(), clock, properties);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
