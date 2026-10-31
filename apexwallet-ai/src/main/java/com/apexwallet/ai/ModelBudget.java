package com.apexwallet.ai;

public final class ModelBudget {

    private final long maxCostMicros;
    private long spentMicros;
    private long calls;

    public ModelBudget(long maxCostMicros) {
        this.maxCostMicros = maxCostMicros;
    }

    public boolean record(int tokens, long microsPerToken, long latencyMillis) {
        calls++;
        spentMicros += (long) tokens * microsPerToken;
        return spentMicros <= maxCostMicros && latencyMillis >= 0;
    }

    public boolean breached() {
        return spentMicros > maxCostMicros;
    }

    public long spentMicros() {
        return spentMicros;
    }

    public long calls() {
        return calls;
    }
}
