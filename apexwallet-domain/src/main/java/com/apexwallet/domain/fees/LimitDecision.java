package com.apexwallet.domain.fees;

public record LimitDecision(boolean allowed, String ruleId, int ruleVersion, String reasonCode, long remainingMinor) {
}
