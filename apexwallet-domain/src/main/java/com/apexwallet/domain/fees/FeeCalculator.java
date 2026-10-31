package com.apexwallet.domain.fees;

import com.apexwallet.domain.money.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fee math uses the same HALF_EVEN policy as {@link Currency#POLICY}. */
public final class FeeCalculator {

    private FeeCalculator() {
    }

    public static FeeQuote quote(FeeRule rule, long amountMinor) {
        if (amountMinor < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        long fee = switch (rule.type()) {
            case FREE -> 0;
            case FIXED -> amountMinor == 0 ? 0 : rule.fixedMinor();
            case PERCENTAGE -> percentage(amountMinor, rule.basisPoints());
            case PERCENTAGE_WITH_CAP -> Math.min(percentage(amountMinor, rule.basisPoints()), rule.capMinor());
        };
        return new FeeQuote(fee, rule.id(), rule.version(), rule.type().name());
    }

    static long percentage(long amountMinor, int basisPoints) {
        return BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_EVEN)
                .longValueExact();
    }
}
