package com.apexwallet.domain.ledger;

import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountStatus;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.money.Currency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure posting rules shared by the in-memory ledger and the database adapter.
 * Nothing is stored here. Callers persist the result only after this returns.
 */
public final class PostingRules {

    private PostingRules() {
    }

    public static PreparedPosting prepare(Posting posting, Map<AccountId, Account> accounts, Map<AccountId, Long> balances) {
        if (posting.legs().size() < 2) {
            throw new DomainException(ErrorCode.UNBALANCED_TRANSACTION, "a posting needs at least two legs");
        }
        Currency currency = posting.legs().get(0).amount().currency();
        long debits = 0;
        long credits = 0;
        for (Posting.Leg leg : posting.legs()) {
            if (leg.amount().currency() != currency) {
                throw new DomainException(ErrorCode.CURRENCY_MISMATCH, "a posting has one currency");
            }
            if (leg.amount().isZero()) {
                throw new DomainException(ErrorCode.NEGATIVE_AMOUNT, "a leg amount must be positive");
            }
            Account account = accounts.get(leg.accountId());
            if (account == null) {
                throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + leg.accountId());
            }
            if (account.status() != AccountStatus.ACTIVE) {
                throw new DomainException(ErrorCode.ACCOUNT_NOT_ACTIVE, "account is " + account.status());
            }
            if (account.currency() != currency) {
                throw new DomainException(ErrorCode.CURRENCY_MISMATCH,
                        "account " + account.id() + " is " + account.currency());
            }
            try {
                if (leg.direction() == Direction.DEBIT) {
                    debits = Math.addExact(debits, leg.amount().amountMinor());
                } else {
                    credits = Math.addExact(credits, leg.amount().amountMinor());
                }
            } catch (ArithmeticException ex) {
                throw new DomainException(ErrorCode.OVERFLOW, "posting amount overflow");
            }
        }
        if (debits != credits) {
            throw new DomainException(ErrorCode.UNBALANCED_TRANSACTION,
                    "debits " + debits + " != credits " + credits);
        }

        Map<AccountId, Long> deltas = new HashMap<>();
        for (Posting.Leg leg : posting.legs()) {
            Account account = accounts.get(leg.accountId());
            long change = account.type().signedDelta(leg.direction(), leg.amount().amountMinor());
            deltas.merge(leg.accountId(), change, Math::addExact);
        }
        for (Map.Entry<AccountId, Long> change : deltas.entrySet()) {
            Account account = accounts.get(change.getKey());
            long current = balances.getOrDefault(account.id(), 0L);
            long next;
            try {
                next = Math.addExact(current, change.getValue());
            } catch (ArithmeticException ex) {
                throw new DomainException(ErrorCode.OVERFLOW, "balance overflow");
            }
            if (next < -account.overdraftLimitMinor()) {
                throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS,
                        "account " + account.id() + " would become " + next);
            }
        }

        TransactionId id = posting.id() == null ? TransactionId.random() : posting.id();
        Instant bookedAt = posting.bookedAt() == null ? Instant.now() : posting.bookedAt();
        List<LedgerEntry> entries = new ArrayList<>();
        int sequence = 1;
        for (Posting.Leg leg : posting.legs()) {
            entries.add(new LedgerEntry(
                    UUID.randomUUID(),
                    id,
                    leg.accountId(),
                    leg.direction(),
                    leg.amount(),
                    bookedAt,
                    sequence++));
        }
        Transaction transaction = new Transaction(
                id,
                posting.type(),
                TransactionStatus.POSTED,
                entries,
                posting.description(),
                posting.reverses(),
                blankToNull(posting.idempotencyKey()),
                bookedAt,
                bookedAt);
        return new PreparedPosting(transaction, Map.copyOf(deltas));
    }

    private static String blankToNull(String key) {
        return key == null || key.isBlank() ? null : key;
    }

    public record PreparedPosting(Transaction transaction, Map<AccountId, Long> deltas) {
    }
}
