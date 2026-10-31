package com.apexwallet.app.persistence;

import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountStatus;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.LedgerEntry;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionId;
import com.apexwallet.domain.ledger.TransactionStatus;
import com.apexwallet.domain.ledger.TransactionType;
import com.apexwallet.domain.money.Currency;
import com.apexwallet.domain.money.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Entities stay in this package. Domain records stay free of JPA. */
final class LedgerMapper {

    private LedgerMapper() {
    }

    static Account toAccount(AccountEntity entity) {
        return new Account(
                new AccountId(entity.getId()),
                entity.getOwnerRef(),
                Currency.valueOf(entity.getCurrency().trim()),
                AccountType.valueOf(entity.getAccountType()),
                AccountStatus.valueOf(entity.getStatus()),
                entity.getOverdraftLimitMinor(),
                null);
    }

    static Transaction toTransaction(TransactionEntity entity) {
        List<LedgerEntry> entries = new ArrayList<>();
        for (LedgerEntryEntity row : entity.getEntries()) {
            entries.add(new LedgerEntry(
                    row.getId(),
                    new TransactionId(row.getTransaction().getId()),
                    new AccountId(row.getAccountId()),
                    Direction.valueOf(row.getDirection()),
                    Money.ofMinor(row.getAmountMinor(), Currency.valueOf(row.getCurrency().trim())),
                    row.getBookedAt(),
                    row.getSeq()));
        }
        entries.sort(Comparator.comparingInt(LedgerEntry::sequence));
        return new Transaction(
                new TransactionId(entity.getId()),
                TransactionType.valueOf(entity.getType()),
                TransactionStatus.valueOf(entity.getStatus()),
                entries,
                entity.getDescription(),
                entity.getReversesTransactionId() == null ? null : new TransactionId(entity.getReversesTransactionId()),
                entity.getIdempotencyKey(),
                entity.getRequestedAt(),
                entity.getPostedAt());
    }
}
