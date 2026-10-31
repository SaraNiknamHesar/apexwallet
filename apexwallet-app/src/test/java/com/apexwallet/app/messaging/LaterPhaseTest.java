package com.apexwallet.app.messaging;

import com.apexwallet.domain.fees.FeeCalculator;
import com.apexwallet.domain.fees.FeeRule;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.apexwallet.app.api.CorrelationScope;
import com.apexwallet.app.metrics.LatencyReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaterPhaseTest {

    @Test
    void relayRetriesThenDeadLettersAndTheConsumerDedupes() {
        MemoryOutbox store = new MemoryOutbox();
        store.add(new OutboxMessage("e1", "alice", "posted", 0));
        store.add(new OutboxMessage("e2", "alice", "posted-2", 0));
        store.add(new OutboxMessage("e3", "bob", "other", 0));
        AtomicInteger calls = new AtomicInteger();
        List<OutboxMessage> dead = new ArrayList<>();
        List<String> aliceOrder = new ArrayList<>();
        OutboxRelay.publish(store, message -> {
            if ("alice".equals(message.accountKey())) {
                aliceOrder.add(message.id());
            }
            if ("e2".equals(message.id()) && calls.incrementAndGet() == 1) {
                throw new IllegalStateException("relay killed");
            }
        }, 2, dead);
        assertTrue(store.isPublished("e1"));
        assertEquals(0, dead.size());
        OutboxRelay.publish(store, message -> {
            if ("e2".equals(message.id())) {
                throw new IllegalStateException("still down");
            }
        }, 2, dead);
        assertEquals(1, dead.size());
        assertEquals("e2", dead.get(0).id());
        assertEquals(List.of("e1", "e2"), aliceOrder);
        IdempotentConsumer consumer = new IdempotentConsumer();
        consumer.onEvent("e1");
        consumer.onEvent("e1");
        assertEquals(1, consumer.effects());
    }

    @Test
    void correlationIdSurvivesAnotherThread() throws Exception {
        MDC.put("correlation_id", "corr-9");
        AtomicReference<String> seen = new AtomicReference<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            CorrelationScope.run(executor, () -> seen.set(MDC.get("correlation_id")));
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            MDC.remove("correlation_id");
        }
        assertEquals("corr-9", seen.get());
    }

    @Test
    void feeBenchmarkReportsPercentilesAfterWarmup() {
        FeeRule rule = FeeRule.percentage("bench", 150);
        for (int i = 0; i < 1_000; i++) {
            FeeCalculator.quote(rule, 10_000);
        }
        long[] samples = new long[200];
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime();
            FeeCalculator.quote(rule, 10_000 + i);
            samples[i] = System.nanoTime() - start;
        }
        long p50 = LatencyReport.percentile(samples, 0.50);
        long p99 = LatencyReport.percentile(samples, 0.99);
        assertTrue(p50 > 0);
        assertTrue(p99 >= p50);
    }
}
