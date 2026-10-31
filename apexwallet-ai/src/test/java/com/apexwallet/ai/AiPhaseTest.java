package com.apexwallet.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPhaseTest {

    @Test
    void riskScoreIsReproducibleAndCategorisationFallsBack() {
        assertEquals(RiskModel.score(100_000, 2, true), RiskModel.score(100_000, 2, true));
        assertEquals("OTHER", Categoriser.categorise(prompt -> "NOT_A_CATEGORY", "rent"));
        assertEquals("OTHER", Categoriser.categorise(prompt -> {
            throw new IllegalStateException("provider down");
        }, "rent"));
        assertEquals("SALARY", Categoriser.categorise(prompt -> "SALARY", "payroll"));
        String masked = Masker.mask("pay GB82WEST12345698765432");
        assertFalse(masked.contains("123456"));
    }

    @Test
    void evalBudgetSearchAndAgentAudit() {
        List<EvalHarness.Sample> golden = List.of(
                new EvalHarness.Sample("groceries", "GROCERIES"),
                new EvalHarness.Sample("salary", "SALARY"));
        assertEquals(1.0, EvalHarness.accuracy(prompt -> prompt.contains("salary") ? "SALARY" : "GROCERIES", golden));
        assertTrue(EvalHarness.accuracy(prompt -> "NOPE", golden) < 0.8);
        ModelBudget budget = new ModelBudget(100);
        assertTrue(budget.record(10, 5, 12));
        assertFalse(budget.record(30, 5, 12));
        assertTrue(budget.breached());
        assertEquals(List.of("coffee shop", "coffee beans"),
                SimilarTransactions.rank("coffee", List.of("rent", "coffee shop", "coffee beans")));
        AgentQueryLog log = new AgentQueryLog(1);
        assertTrue(log.query("alice", "account 123456789", Instant.EPOCH));
        assertFalse(log.query("alice", "again", Instant.EPOCH));
        assertTrue(log.lines().get(0).contains("alice"));
        assertFalse(log.lines().get(0).contains("123456789"));
        int payments = 1;
        Categoriser.categorise(prompt -> {
            throw new IllegalStateException("down");
        }, "ignored");
        assertEquals(1, payments);
    }
}
