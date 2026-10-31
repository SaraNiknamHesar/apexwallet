package com.apexwallet.domain.ledger;

import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.money.Currency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory double-entry journal for one JVM.
 * The monitor on this object is the Phase 3 answer for a single process.
 * Two JVMs need the database row lock instead. See ADR 0002.
 */
public final class Ledger {

    private final Map<AccountId, Account> accounts = new HashMap<>();
    private final Map<AccountId, Long> cachedBalance = new HashMap<>();
    private final Map<AccountId, List<LedgerEntry>> entriesByAccount = new HashMap<>();
    private final Map<TransactionId, Transaction> transactions = new LinkedHashMap<>();
    private final Map<String, TransactionId> idempotencyIndex = new HashMap<>();

    public synchronized Account open(Account account) {
        if (accounts.containsKey(account.id())) {
            throw new DomainException(ErrorCode.DUPLICATE, "account already open: " + account.id());
        }
        accounts.put(account.id(), account);
        cachedBalance.put(account.id(), 0L);
        entriesByAccount.put(account.id(), new ArrayList<>());
        return account;
    }

    public synchronized Optional<Account> find(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public synchronized List<Account> all() {
        return List.copyOf(accounts.values());
    }

    public synchronized List<Account> findByOwner(String ownerRef) {
        return accounts.values().stream()
                .filter(account -> account.ownerRef().equals(ownerRef))
                .toList();
    }

    public synchronized Account replace(Account account) {
        if (!accounts.containsKey(account.id())) {
            throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + account.id());
        }
        accounts.put(account.id(), account);
        return account;
    }

    public synchronized Optional<Transaction> findTransaction(TransactionId id) {
        return Optional.ofNullable(transactions.get(id));
    }

    public synchronized Optional<Transaction> findByIdempotencyKey(String key) {
        TransactionId id = idempotencyIndex.get(key);
        return id == null ? Optional.empty() : Optional.ofNullable(transactions.get(id));
    }

    public synchronized long balanceOf(AccountId accountId) {
        requireAccount(accountId);
        return cachedBalance.getOrDefault(accountId, 0L);
    }

    public synchronized List<LedgerEntry> entries(AccountId accountId) {
        requireAccount(accountId);
        return List.copyOf(entriesByAccount.get(accountId));
    }

    public synchronized int transactionCount() {
        return transactions.size();
    }

    /**
     * Validates the whole posting first. An unbalanced transaction throws before any entry is stored.
     * The same idempotency key returns the original transaction and does not post again (I5).
     */
    public synchronized Transaction post(Posting posting) {
        if (posting.idempotencyKey() != null && !posting.idempotencyKey().isBlank()) {
            TransactionId existing = idempotencyIndex.get(posting.idempotencyKey());
            if (existing != null) {
                return transactions.get(existing);
            }
        }
        PostingRules.PreparedPosting prepared = PostingRules.prepare(posting, accounts, cachedBalance);
        Transaction transaction = prepared.transaction();
        if (transactions.containsKey(transaction.id())) {
            throw new DomainException(ErrorCode.DUPLICATE, "transaction id already used");
        }
        for (LedgerEntry entry : transaction.entries()) {
            entriesByAccount.get(entry.accountId()).add(entry);
        }
        for (Map.Entry<AccountId, Long> change : prepared.deltas().entrySet()) {
            cachedBalance.merge(change.getKey(), change.getValue(), Math::addExact);
        }
        transactions.put(transaction.id(), transaction);
        if (transaction.idempotencyKey() != null) {
            idempotencyIndex.put(transaction.idempotencyKey(), transaction.id());
        }
        assertBalanced(transaction);
        return transaction;
    }

    public synchronized Transaction reverse(TransactionId originalId, String description) {
        Transaction original = transactions.get(originalId);
        if (original == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "transaction not found: " + originalId);
        }
        if (original.status() != TransactionStatus.POSTED) {
            throw new DomainException(ErrorCode.ILLEGAL_TRANSITION,
                    "cannot reverse status " + original.status());
        }
        List<Posting.Leg> legs = new ArrayList<>();
        for (LedgerEntry entry : original.entries()) {
            legs.add(new Posting.Leg(entry.accountId(), entry.direction().opposite(), entry.amount()));
        }
        Transaction reversal = post(new Posting(
                TransactionId.random(),
                TransactionType.REVERSAL,
                legs,
                description == null ? "reversal of " + originalId : description,
                originalId,
                null,
                Instant.now()));
        transactions.put(originalId, original.withStatus(TransactionStatus.REVERSED));
        return reversal;
    }

    public synchronized Map<AccountId, Long> rebuildBalances() {
        Map<AccountId, Long> rebuilt = new HashMap<>();
        for (Account account : accounts.values()) {
            long sum = 0;
            for (LedgerEntry entry : entriesByAccount.get(account.id())) {
                sum = Math.addExact(sum, account.type().signedDelta(entry.direction(), entry.amount().amountMinor()));
            }
            rebuilt.put(account.id(), sum);
        }
        if (!rebuilt.equals(cachedBalance)) {
            throw new IllegalStateException("cached balance diverged from the journal");
        }
        return Map.copyOf(rebuilt);
    }

    /** I2: for every currency, signed debits minus credits across the whole journal is zero. */
    public synchronized Map<Currency, Long> netByCurrency() {
        Map<Currency, Long> nets = new HashMap<>();
        for (Transaction transaction : transactions.values()) {
            for (LedgerEntry entry : transaction.entries()) {
                long signed = entry.direction() == Direction.DEBIT
                        ? entry.amount().amountMinor()
                        : -entry.amount().amountMinor();
                nets.merge(entry.amount().currency(), signed, Math::addExact);
            }
        }
        return nets;
    }

    public synchronized Map<String, Long> cachedByAccount() {
        Map<String, Long> cached = new LinkedHashMap<>();
        for (Account account : accounts.values()) {
            cached.put(account.id().value().toString(), cachedBalance.getOrDefault(account.id(), 0L));
        }
        return Map.copyOf(cached);
    }

    public synchronized Map<String, Long> foldedByAccount() {
        Map<String, Long> folded = new LinkedHashMap<>();
        for (Account account : accounts.values()) {
            long sum = 0;
            for (LedgerEntry entry : entriesByAccount.get(account.id())) {
                sum = Math.addExact(sum, account.type().signedDelta(entry.direction(), entry.amount().amountMinor()));
            }
            folded.put(account.id().value().toString(), sum);
        }
        return Map.copyOf(folded);
    }

    public synchronized List<String> descriptions() {
        return transactions.values().stream().map(Transaction::description).filter(text -> !text.isBlank()).toList();
    }

    public synchronized AccountStatement statement(AccountId accountId) {
        Account account = requireAccount(accountId);
        List<LedgerEntry> ordered = new ArrayList<>(entriesByAccount.get(accountId));
        // List.sort is stable, so equal timestamps keep journal (append) order.
        List<StatementLine> lines = new ArrayList<>();
        long running = 0;
        for (LedgerEntry entry : ordered) {
            running = Math.addExact(running, account.type().signedDelta(entry.direction(), entry.amount().amountMinor()));
            lines.add(new StatementLine(
                    entry.bookedAt(),
                    entry.sequence(),
                    entry.id(),
                    entry.transactionId(),
                    entry.direction(),
                    entry.amount().amountMinor(),
                    running,
                    descriptionOf(entry.transactionId())));
        }
        return new AccountStatement(accountId, account.currency(), lines, running);
    }

    private String descriptionOf(TransactionId id) {
        Transaction transaction = transactions.get(id);
        return transaction == null ? "" : transaction.description();
    }

    private Account requireAccount(AccountId id) {
        Account account = accounts.get(id);
        if (account == null) {
            throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id);
        }
        return account;
    }

    private static void assertBalanced(Transaction transaction) {
        long debits = 0;
        long credits = 0;
        for (LedgerEntry entry : transaction.entries()) {
            if (entry.direction() == Direction.DEBIT) {
                debits += entry.amount().amountMinor();
            } else {
                credits += entry.amount().amountMinor();
            }
        }
        if (debits != credits) {
            throw new IllegalStateException("I1 broken after post");
        }
    }
}
