package com.apexwallet.domain.ledger;

import com.apexwallet.domain.account.AccountId;

import java.time.Instant;
import java.util.UUID;

public record StatementLine(
        Instant bookedAt,
        int sequence,
        UUID entryId,
        TransactionId transactionId,
        Direction direction,
        long amountMinor,
        long runningBalanceMinor,
        String description
) {
    public StatementLine {
        description = description == null ? "" : description;
    }
}
