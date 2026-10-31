package com.apexwallet.domain.fees;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Interest in minor units. ACT_360 and ACT_365 use the actual day count.
 * THIRTY_360 uses 30-day months and caps each day-of-month at 30.
 * Rounding is HALF_EVEN, the same policy as money.
 */
public final class Interest {

    private Interest() {
    }

    public static long accruedMinor(long principalMinor, BigDecimal annualRate, LocalDate from, LocalDate to, DayCount convention) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("period ends before it starts");
        }
        long days = switch (convention) {
            case ACT_360, ACT_365 -> ChronoUnit.DAYS.between(from, to);
            case THIRTY_360 -> thirty360(from, to);
        };
        int denominator = convention == DayCount.ACT_365 ? 365 : 360;
        return BigDecimal.valueOf(principalMinor)
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_EVEN)
                .longValueExact();
    }

    static long thirty360(LocalDate from, LocalDate to) {
        int d1 = Math.min(from.getDayOfMonth(), 30);
        int d2 = Math.min(to.getDayOfMonth(), 30);
        return 360L * (to.getYear() - from.getYear())
                + 30L * (to.getMonthValue() - from.getMonthValue())
                + (d2 - d1);
    }
}
