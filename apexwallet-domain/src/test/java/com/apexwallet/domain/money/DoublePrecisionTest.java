package com.apexwallet.domain.money;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DoublePrecisionTest {

    @Test
    void binaryFractionsAreNotDecimalMoney() {
        double sum = 0.1 + 0.2;
        assertNotEquals(0.3, sum);
        assertEquals("0.30000000000000004", Double.toString(sum));

        double change = 1.0 - 0.9;
        assertNotEquals(0.1, change);
        assertEquals("0.09999999999999998", Double.toString(change));
    }
}
