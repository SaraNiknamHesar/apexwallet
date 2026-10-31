package com.apexwallet.domain.money;

import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A non-negative amount in one currency, stored as minor units (cents, yen, fils).
 * There is no shared {@code ZERO}: zero euros and zero yen are different values.
 */
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (amountMinor < 0) {
            throw new DomainException(ErrorCode.NEGATIVE_AMOUNT, "amount must be >= 0 minor units");
        }
    }

    public static Money ofMinor(long amountMinor, Currency currency) {
        return new Money(amountMinor, currency);
    }

    public static Money ofMajor(BigDecimal major, Currency currency) {
        return new Money(currency.toMinorUnits(major), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public BigDecimal majorAmount() {
        return currency.toMajor(amountMinor);
    }

    public Money plus(Money other) {
        sameCurrency(other);
        return new Money(add(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        sameCurrency(other);
        long result = subtract(amountMinor, other.amountMinor);
        if (result < 0) {
            throw new DomainException(ErrorCode.NEGATIVE_AMOUNT,
                    "subtraction would be negative: " + amountMinor + " - " + other.amountMinor);
        }
        return new Money(result, currency);
    }

    public Money times(BigDecimal factor) {
        return times(factor, Currency.POLICY);
    }

    public Money times(BigDecimal factor, RoundingMode mode) {
        Objects.requireNonNull(factor, "factor");
        Objects.requireNonNull(mode, "mode");
        if (factor.signum() < 0) {
            throw new DomainException(ErrorCode.NEGATIVE_AMOUNT, "factor must be >= 0");
        }
        try {
            long scaled = new BigDecimal(amountMinor).multiply(factor).setScale(0, mode).longValueExact();
            return new Money(scaled, currency);
        } catch (ArithmeticException ex) {
            throw new DomainException(ErrorCode.OVERFLOW, "multiplication overflow");
        }
    }

    /**
     * Split this amount by positive weights. The parts sum back to this amount exactly;
     * leftover minor units go to the largest fractional remainders, ties broken by lowest index.
     */
    public List<Money> allocate(long... ratios) {
        if (ratios == null || ratios.length == 0) {
            throw new IllegalArgumentException("at least one ratio is required");
        }
        long weight = 0;
        for (long ratio : ratios) {
            if (ratio < 0) {
                throw new IllegalArgumentException("ratio must be >= 0");
            }
            weight = add(weight, ratio);
        }
        if (weight == 0) {
            throw new IllegalArgumentException("ratios must sum to a positive weight");
        }

        long[] shares = new long[ratios.length];
        long[] remainder = new long[ratios.length];
        long distributed = 0;
        BigInteger total = BigInteger.valueOf(weight);
        for (int i = 0; i < ratios.length; i++) {
            BigInteger[] div = BigInteger.valueOf(amountMinor)
                    .multiply(BigInteger.valueOf(ratios[i]))
                    .divideAndRemainder(total);
            shares[i] = div[0].longValueExact();
            remainder[i] = div[1].longValueExact();
            distributed = add(distributed, shares[i]);
        }
        long left = amountMinor - distributed;
        Integer[] order = new Integer[ratios.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator
                .comparingLong((Integer i) -> remainder[i]).reversed()
                .thenComparingInt(i -> i));
        for (int k = 0; k < left; k++) {
            shares[order[k]]++;
        }

        List<Money> parts = new ArrayList<>(ratios.length);
        long check = 0;
        for (long share : shares) {
            parts.add(new Money(share, currency));
            check = add(check, share);
        }
        if (check != amountMinor) {
            throw new IllegalStateException("allocate lost minor units");
        }
        return List.copyOf(parts);
    }

    @Override
    public int compareTo(Money other) {
        sameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    private void sameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (currency != other.currency) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH,
                    "cannot combine " + currency + " with " + other.currency);
        }
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new DomainException(ErrorCode.OVERFLOW, "addition overflow");
        }
    }

    private static long subtract(long left, long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException ex) {
            throw new DomainException(ErrorCode.OVERFLOW, "subtraction overflow");
        }
    }
}
