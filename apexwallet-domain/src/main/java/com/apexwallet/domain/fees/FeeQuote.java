package com.apexwallet.domain.fees;

public record FeeQuote(long feeMinor, String ruleId, int ruleVersion, String reasonCode) {
}
