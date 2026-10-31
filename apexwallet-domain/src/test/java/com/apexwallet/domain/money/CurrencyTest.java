package com.apexwallet.domain.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyTest {

    @Test
    void convertsIsoMinorUnits() {
        assertEquals(123, Currency.EUR.toMinorUnits(new BigDecimal("1.23")));
        assertEquals(123, Currency.USD.toMinorUnits(new BigDecimal("1.23")));
        assertEquals(123, Currency.JPY.toMinorUnits(new BigDecimal("123")));
        assertEquals(1234, Currency.KWD.toMinorUnits(new BigDecimal("1.234")));

        assertEquals(new BigDecimal("1.23"), Currency.EUR.toMajor(123));
        assertEquals(new BigDecimal("123"), Currency.JPY.toMajor(123));
        assertEquals(new BigDecimal("1.234"), Currency.KWD.toMajor(1234));
    }

    @Test
    void halfEvenIsThePolicy() {
        assertEquals(RoundingMode(), Currency.POLICY);
        assertEquals(124, Currency.EUR.toMinorUnits(new BigDecimal("1.235")));
        assertEquals(122, Currency.EUR.toMinorUnits(new BigDecimal("1.225")));
    }

    private static java.math.RoundingMode RoundingMode() {
        return java.math.RoundingMode.HALF_EVEN;
    }
}
