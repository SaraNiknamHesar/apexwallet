package com.apexwallet.domain.fees;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/** One lock per account-day so concurrent transfers cannot pass a daily limit by one minor unit. */
public final class DailyLimit {

    private final String ruleId;
    private final long limitMinor;
    private final ConcurrentHashMap<String, Long> used = new ConcurrentHashMap<>();

    public DailyLimit(String ruleId, long limitMinor) {
        this.ruleId = ruleId;
        this.limitMinor = limitMinor;
    }

    public LimitDecision tryConsume(String accountId, LocalDate day, long amountMinor) {
        String key = accountId + "|" + day;
        synchronized (key.intern()) {
            long already = used.getOrDefault(key, 0L);
            if (Math.addExact(already, amountMinor) > limitMinor) {
                return new LimitDecision(false, ruleId, 1, "DAILY_LIMIT", limitMinor - already);
            }
            used.put(key, already + amountMinor);
            return new LimitDecision(true, ruleId, 1, "WITHIN_LIMIT", limitMinor - already - amountMinor);
        }
    }
}
