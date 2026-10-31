package com.apexwallet.domain.money;

import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void arithmeticStaysInsideOneCurrency() {
        Money a = Money.ofMinor(150, Currency.EUR);
        Money b = Money.ofMinor(40, Currency.EUR);
        assertEquals(190, a.plus(b).amountMinor());
        assertEquals(110, a.minus(b).amountMinor());
        assertTrue(a.compareTo(b) > 0);
        assertTrue(Money.zero(Currency.EUR).isZero());
        assertEquals(ErrorCode.CURRENCY_MISMATCH,
                assertThrows(DomainException.class, () -> a.plus(Money.ofMinor(1, Currency.JPY))).code());
    }

    @Test
    void rejectsNegativeAndOverflow() {
        assertEquals(ErrorCode.NEGATIVE_AMOUNT,
                assertThrows(DomainException.class, () -> Money.ofMinor(-1, Currency.EUR)).code());
        Money huge = Money.ofMinor(Long.MAX_VALUE, Currency.USD);
        assertEquals(ErrorCode.OVERFLOW,
                assertThrows(DomainException.class, () -> huge.plus(Money.ofMinor(1, Currency.USD))).code());
    }

    @Test
    void timesUsesHalfEvenAndNeverDouble() {
        Money fee = Money.ofMinor(10, Currency.EUR).times(new BigDecimal("0.5"));
        assertEquals(5, fee.amountMinor());
        Money half = Money.ofMinor(5, Currency.EUR).times(new BigDecimal("0.5"));
        assertEquals(2, half.amountMinor());
    }

    @Test
    void allocateKnownSplits() {
        List<Money> even = Money.ofMinor(100, Currency.EUR).allocate(1, 1, 1);
        assertEquals(34, even.get(0).amountMinor());
        assertEquals(33, even.get(1).amountMinor());
        assertEquals(33, even.get(2).amountMinor());

        List<Money> weighted = Money.ofMinor(10, Currency.EUR).allocate(1, 2);
        assertEquals(3, weighted.get(0).amountMinor());
        assertEquals(7, weighted.get(1).amountMinor());
    }

    @Property(tries = 80)
    void allocateLosesNoMinorUnit(@ForAll @LongRange(min = 0, max = 1_000_000) long amount,
                                   @ForAll @LongRange(min = 1, max = 20) long first,
                                   @ForAll @LongRange(min = 0, max = 20) long second,
                                   @ForAll @LongRange(min = 0, max = 20) long third) {
        List<Money> parts = Money.ofMinor(amount, Currency.KWD).allocate(first, second, third);
        long sum = parts.stream().mapToLong(Money::amountMinor).sum();
        assertEquals(amount, sum);
        assertEquals(3, parts.size());
    }
}
