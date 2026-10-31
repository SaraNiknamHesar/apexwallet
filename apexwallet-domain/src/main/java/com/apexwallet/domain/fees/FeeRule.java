package com.apexwallet.domain.fees;

public record FeeRule(String id, int version, FeeRuleType type, long fixedMinor, int basisPoints, long capMinor) {

    public static FeeRule fixed(String id, long fixedMinor) {
        return new FeeRule(id, 1, FeeRuleType.FIXED, fixedMinor, 0, 0);
    }

    public static FeeRule percentage(String id, int basisPoints) {
        return new FeeRule(id, 1, FeeRuleType.PERCENTAGE, 0, basisPoints, 0);
    }

    public static FeeRule percentageWithCap(String id, int basisPoints, long capMinor) {
        return new FeeRule(id, 1, FeeRuleType.PERCENTAGE_WITH_CAP, 0, basisPoints, capMinor);
    }

    public static FeeRule free(String id) {
        return new FeeRule(id, 1, FeeRuleType.FREE, 0, 0, 0);
    }
}
