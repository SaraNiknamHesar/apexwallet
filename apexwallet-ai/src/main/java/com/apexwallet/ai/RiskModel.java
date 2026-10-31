package com.apexwallet.ai;

/** Deterministic score. The same features always return the same number. No model call. */
public final class RiskModel {

    private RiskModel() {
    }

    public static RiskScore score(long amountMinor, int paymentsToday, boolean newPayee) {
        int score = 0;
        if (amountMinor >= 100_000) {
            score += 40;
        } else if (amountMinor >= 10_000) {
            score += 15;
        }
        score += Math.min(paymentsToday, 10) * 3;
        if (newPayee) {
            score += 20;
        }
        String reason = score >= 50 ? "REVIEW" : "PASS";
        return new RiskScore(Math.min(score, 100), reason);
    }
}
