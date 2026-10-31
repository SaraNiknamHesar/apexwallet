package com.apexwallet.domain.ledger;

import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.money.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A request to post. The ledger validates it before any state changes. */
public record Posting(
        TransactionId id,
        TransactionType type,
        List<Leg> legs,
        String description,
        TransactionId reverses,
        String idempotencyKey,
        Instant bookedAt
) {
    public Posting {
        Objects.requireNonNull(type, "type");
        legs = legs == null ? List.of() : List.copyOf(legs);
        description = description == null ? "" : description;
    }

    public record Leg(AccountId accountId, Direction direction, Money amount) {
        public Leg {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
