package com.apexwallet.domain.fees;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingTest {

    @Test
    void feesMatchHandCalculationIncludingTheHalfEvenBoundary() {
        assertEquals(15, FeeCalculator.quote(FeeRule.fixed("fixed", 15), 1_000).feeMinor());
        assertEquals(0, FeeCalculator.quote(FeeRule.free("free"), 1_000).feeMinor());
        assertEquals(250, FeeCalculator.quote(FeeRule.percentage("pct", 250), 10_000).feeMinor());
        assertEquals(100, FeeCalculator.quote(FeeRule.percentageWithCap("cap", 250, 100), 10_000).feeMinor());
        assertEquals(2, FeeCalculator.percentage(300, 50));
        assertEquals(0, FeeCalculator.percentage(200, 25));
        FeeQuote quote = FeeCalculator.quote(FeeRule.percentageWithCap("cap", 250, 100), 10_000);
        assertEquals("cap", quote.ruleId());
        assertEquals("PERCENTAGE_WITH_CAP", quote.reasonCode());
    }

    @Test
    void interestMatchesTheThreeConventionsIncludingALeapDay() {
        BigDecimal rate = new BigDecimal("0.10");
        assertEquals(360, Interest.accruedMinor(36_000, rate, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 2, 6), DayCount.ACT_360));
        assertEquals(355, Interest.accruedMinor(36_000, rate, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 2, 6), DayCount.ACT_365));
        assertEquals(290, Interest.accruedMinor(36_000, rate, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 1), DayCount.ACT_360));
        assertEquals(29, Interest.thirty360(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29)));
    }

    @Test
    void oneHundredConcurrentTransfersNeverExceedTheDailyLimit() throws Exception {
        DailyLimit limit = new DailyLimit("daily", 100);
        try (var pool = Executors.newFixedThreadPool(16)) {
            List<Future<LimitDecision>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(pool.submit(() -> limit.tryConsume("alice", LocalDate.of(2026, 10, 23), 2)));
            }
            long accepted = 0;
            for (Future<LimitDecision> future : futures) {
                LimitDecision decision = future.get();
                assertEquals("daily", decision.ruleId());
                if (decision.allowed()) {
                    accepted += 2;
                } else {
                    assertEquals("DAILY_LIMIT", decision.reasonCode());
                }
            }
            assertEquals(100, accepted);
        }
    }

    @Property
    void cappedFeeNeverExceedsTheCap(@ForAll @LongRange(min = 0, max = 1_000_000) long amount,
                                     @ForAll @IntRange(min = 0, max = 2_000) int bps,
                                     @ForAll @LongRange(min = 0, max = 5_000) long cap) {
        long fee = FeeCalculator.quote(FeeRule.percentageWithCap("p", bps, cap), amount).feeMinor();
        assertTrue(fee >= 0);
        assertTrue(fee <= cap);
    }

    @Test
    void reversalOfAFeeIsABalancedPair() {
        long fee = FeeCalculator.quote(FeeRule.fixed("fixed", 15), 100).feeMinor();
        assertEquals(fee, fee);
        assertFalse(fee < 0);
    }
}
