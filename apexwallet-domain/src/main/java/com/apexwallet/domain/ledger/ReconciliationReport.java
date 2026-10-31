package com.apexwallet.domain.ledger;

import java.util.List;

public record ReconciliationReport(List<String> mismatches, long checksumMinor) {

    public boolean clean() {
        return mismatches.isEmpty();
    }
}
