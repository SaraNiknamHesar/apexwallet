package com.apexwallet.domain.ledger;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationTest {

    @Test
    void aCleanFoldPassesAndAPlantedMismatchIsNamed() {
        ReconciliationReport clean = Reconciliation.compare(Map.of("a", 10L, "b", 5L), Map.of("a", 10L, "b", 5L));
        assertTrue(clean.clean());
        assertEquals(15, clean.checksumMinor());
        ReconciliationReport dirty = Reconciliation.compare(Map.of("a", 10L), Map.of("a", 9L));
        assertFalse(dirty.clean());
        assertTrue(dirty.mismatches().get(0).contains("cache=10"));
    }
}
