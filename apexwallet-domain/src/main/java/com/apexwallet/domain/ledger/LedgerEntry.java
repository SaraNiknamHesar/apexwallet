package com.apexwallet.domain.ledger;

import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.money.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
        UUID id,
        TransactionId transactionId,
        AccountId accountId,
        Direction direction,
        Money amount,
        Instant bookedAt,
        int sequence
) {
    public LedgerEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(bookedAt, "bookedAt");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence starts at 1");
        }
        if (amount.amountMinor() <= 0) {
            throw new IllegalArgumentException("entry amount must be positive");
        }
    }
}
