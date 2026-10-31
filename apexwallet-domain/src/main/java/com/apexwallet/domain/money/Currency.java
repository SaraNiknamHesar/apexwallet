package com.apexwallet.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * ISO 4217 currencies this project actually supports.
 * Minor-unit counts are from the standard, not guessed: JPY has 0, KWD has 3.
 */
public enum Currency {
    EUR(2),
    USD(2),
    GBP(2),
    CHF(2),
    JPY(0),
    KRW(0),
    KWD(3),
    BHD(3),
    OMR(3),
    TND(3);

    /** Banker's rounding. A policy decision: HALF_UP would bias every half-cent upward. */
    public static final RoundingMode POLICY = RoundingMode.HALF_EVEN;

    private final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }

    public int minorUnits() {
        return minorUnits;
    }

    public String isoCode() {
        return name();
    }

    public static Currency fromIso(String isoCode) {
        Objects.requireNonNull(isoCode, "isoCode");
        try {
            return Currency.valueOf(isoCode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported currency: " + isoCode);
        }
    }

    public long toMinorUnits(BigDecimal major) {
        Objects.requireNonNull(major, "major");
        return major.movePointRight(minorUnits).setScale(0, POLICY).longValueExact();
    }

    public BigDecimal toMajor(long amountMinor) {
        return BigDecimal.valueOf(amountMinor, minorUnits);
    }
}
