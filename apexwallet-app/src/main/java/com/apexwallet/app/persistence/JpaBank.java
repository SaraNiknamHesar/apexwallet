package com.apexwallet.app.persistence;

import com.apexwallet.app.port.Bank;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.ledger.AccountStatement;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.LedgerEntry;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.PostingRules;
import com.apexwallet.domain.ledger.Reconciliation;
import com.apexwallet.domain.ledger.ReconciliationReport;
import com.apexwallet.domain.ledger.StatementLine;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionId;
import com.apexwallet.domain.ledger.TransactionStatus;
import com.apexwallet.domain.ledger.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 5 ledger. Domain rules stay in {@link PostingRules}. Lock order is a native
 * {@code SELECT … FOR UPDATE ORDER BY id} because the database, not the ORM, must take the locks.
 */
@Component
@Profile("postgres")
public class JpaBank implements Bank {

    private final TransactionTemplate transactions;

    @PersistenceContext
    private EntityManager em;

    public JpaBank(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public Account open(Account account) {
        return transactions.execute(status -> {
            if (em.find(AccountEntity.class, account.id().value()) != null) {
                throw new DomainException(ErrorCode.DUPLICATE, "account already open: " + account.id());
            }
            AccountEntity entity = new AccountEntity();
            entity.setId(account.id().value());
            entity.setOwnerRef(account.ownerRef());
            entity.setCurrency(account.currency().name());
            entity.setStatus(account.status().name());
            entity.setAccountType(account.type().name());
            entity.setOverdraftLimitMinor(account.overdraftLimitMinor());
            entity.setCachedBalanceMinor(0);
            entity.setCreatedAt(Instant.now());
            em.persist(entity);
            return account.withExternalNumber(null);
        });
    }

    @Override
    public Optional<Account> find(AccountId id) {
        return Optional.ofNullable(transactions.execute(status -> {
            AccountEntity entity = em.find(AccountEntity.class, id.value());
            return entity == null ? null : LedgerMapper.toAccount(entity);
        }));
    }

    @Override
    public List<Account> findAll() {
        return transactions.execute(status -> em.createQuery(
                        "select a from AccountEntity a order by a.id", AccountEntity.class)
                .getResultList()
                .stream()
                .map(LedgerMapper::toAccount)
                .toList());
    }

    @Override
    public List<Account> findByOwner(String ownerRef) {
        return transactions.execute(status -> em.createQuery(
                        "select a from AccountEntity a where a.ownerRef = :owner order by a.id", AccountEntity.class)
                .setParameter("owner", ownerRef)
                .getResultList()
                .stream()
                .map(LedgerMapper::toAccount)
                .toList());
    }

    @Override
    public Transaction post(Posting posting) {
        return post(posting, null);
    }

    /** {@code beforeCommit} runs after the flush and before the transaction commits. */
    public Transaction post(Posting posting, Runnable beforeCommit) {
        try {
            return DeadlockRetry.run(5, () -> transactions.execute(status -> write(posting, beforeCommit)),
                    DeadlockRetry::isDeadlock);
        } catch (IdempotencyRace race) {
            return loadWinner(race.key());
        } catch (RuntimeException ex) {
            String key = blankToNull(posting.idempotencyKey());
            if (key != null && isUniqueViolation(ex)) {
                return loadWinner(key);
            }
            throw ex;
        }
    }

    @Override
    public Transaction reverse(TransactionId id, String description) {
        return DeadlockRetry.run(5, () -> transactions.execute(status -> {
            TransactionEntity original = em.createQuery("""
                            select t from TransactionEntity t
                            left join fetch t.entries
                            where t.id = :id
                            """, TransactionEntity.class)
                    .setParameter("id", id.value())
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "transaction not found: " + id));
            if (!TransactionStatus.POSTED.name().equals(original.getStatus())) {
                throw new DomainException(ErrorCode.ILLEGAL_TRANSITION, "cannot reverse status " + original.getStatus());
            }
            List<Posting.Leg> legs = new ArrayList<>();
            for (LedgerEntryEntity entry : original.getEntries()) {
                legs.add(new Posting.Leg(
                        new AccountId(entry.getAccountId()),
                        Direction.valueOf(entry.getDirection()).opposite(),
                        com.apexwallet.domain.money.Money.ofMinor(
                                entry.getAmountMinor(),
                                com.apexwallet.domain.money.Currency.valueOf(entry.getCurrency().trim()))));
            }
            Transaction reversal = write(new Posting(
                    TransactionId.random(), TransactionType.REVERSAL, legs,
                    description == null ? "reversal of " + id : description,
                    id, null, Instant.now()), null);
            original.setStatus(TransactionStatus.REVERSED.name());
            em.flush();
            return reversal;
        }), DeadlockRetry::isDeadlock);
    }

    @Override
    public long balanceOf(AccountId id) {
        Long balance = transactions.execute(status -> em.createQuery(
                        "select a.cachedBalanceMinor from AccountEntity a where a.id = :id", Long.class)
                .setParameter("id", id.value())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id)));
        return balance;
    }

    @Override
    public AccountStatement statement(AccountId id) {
        return transactions.execute(status -> {
            AccountEntity account = em.find(AccountEntity.class, id.value());
            if (account == null) {
                throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id);
            }
            List<Object[]> rows = em.createQuery("""
                            select e.id, e.seq, e.bookedAt, e.direction, e.amountMinor, t.id, t.description
                            from LedgerEntryEntity e join e.transaction t
                            where e.accountId = :id
                            order by e.bookedAt, e.seq
                            """, Object[].class)
                    .setParameter("id", id.value())
                    .getResultList();
            Account domain = LedgerMapper.toAccount(account);
            List<StatementLine> lines = new ArrayList<>();
            long running = 0;
            for (Object[] row : rows) {
                Direction direction = Direction.valueOf((String) row[3]);
                long amount = (Long) row[4];
                running = Math.addExact(running, domain.type().signedDelta(direction, amount));
                lines.add(new StatementLine(
                        (Instant) row[2],
                        (Integer) row[1],
                        (UUID) row[0],
                        new TransactionId((UUID) row[5]),
                        direction,
                        amount,
                        running,
                        (String) row[6]));
            }
            return new AccountStatement(id, domain.currency(), lines, running);
        });
    }

    @Override
    public Account changeOverdraft(AccountId id, long newLimitMinor, long expectedVersion) {
        if (newLimitMinor < 0) {
            throw new DomainException(ErrorCode.NEGATIVE_AMOUNT, "overdraft limit must be >= 0");
        }
        try {
            return transactions.execute(status -> {
                AccountEntity entity = em.find(AccountEntity.class, id.value());
                if (entity == null) {
                    throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id);
                }
                if (entity.getVersion() == null || entity.getVersion() != expectedVersion) {
                    throw new DomainException(ErrorCode.OPTIMISTIC_LOCK,
                            "version " + expectedVersion + " is stale; current is " + entity.getVersion());
                }
                entity.setOverdraftLimitMinor(newLimitMinor);
                em.flush();
                return LedgerMapper.toAccount(entity);
            });
        } catch (RuntimeException ex) {
            if (isOptimistic(ex)) {
                throw new DomainException(ErrorCode.OPTIMISTIC_LOCK, "version " + expectedVersion + " is stale");
            }
            throw ex;
        }
    }

    @Override
    public long versionOf(AccountId id) {
        Long version = transactions.execute(status -> {
            AccountEntity entity = em.find(AccountEntity.class, id.value());
            if (entity == null) {
                throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id);
            }
            return entity.getVersion();
        });
        return version;
    }

    @Override
    public ReconciliationReport reconcile() {
        return transactions.execute(status -> {
            List<AccountEntity> accounts = em.createQuery("select a from AccountEntity a", AccountEntity.class).getResultList();
            List<LedgerEntryEntity> entries = em.createQuery("select e from LedgerEntryEntity e", LedgerEntryEntity.class).getResultList();
            Map<UUID, Account> byId = new HashMap<>();
            Map<String, Long> cached = new LinkedHashMap<>();
            Map<String, Long> folded = new LinkedHashMap<>();
            for (AccountEntity entity : accounts) {
                Account account = LedgerMapper.toAccount(entity);
                byId.put(entity.getId(), account);
                cached.put(entity.getId().toString(), entity.getCachedBalanceMinor());
                folded.put(entity.getId().toString(), 0L);
            }
            for (LedgerEntryEntity entry : entries) {
                Account account = byId.get(entry.getAccountId());
                if (account == null) {
                    continue;
                }
                String id = entry.getAccountId().toString();
                long delta = account.type().signedDelta(Direction.valueOf(entry.getDirection()), entry.getAmountMinor());
                folded.put(id, Math.addExact(folded.get(id), delta));
            }
            return Reconciliation.compare(cached, folded);
        });
    }

    @Override
    public List<String> descriptions() {
        List<String> rows = transactions.execute(status -> em.createQuery(
                        "select t.description from TransactionEntity t", String.class)
                .getResultList());
        if (rows == null) {
            return List.of();
        }
        return rows.stream().filter(text -> text != null && !text.isBlank()).toList();
    }

    private Transaction write(Posting posting, Runnable beforeCommit) {
        String key = blankToNull(posting.idempotencyKey());
        if (key != null) {
            Transaction existing = findByKey(key);
            if (existing != null) {
                return existing;
            }
        }
        List<UUID> accountIds = posting.legs().stream()
                .map(leg -> leg.accountId().value())
                .distinct()
                .sorted()
                .toList();
        Map<UUID, AccountEntity> locked = lockInIdOrder(accountIds);
        Map<AccountId, Account> accounts = new HashMap<>();
        Map<AccountId, Long> balances = new HashMap<>();
        for (AccountEntity entity : locked.values()) {
            Account account = LedgerMapper.toAccount(entity);
            accounts.put(account.id(), account);
            balances.put(account.id(), entity.getCachedBalanceMinor());
        }
        PostingRules.PreparedPosting prepared = PostingRules.prepare(posting, accounts, balances);
        Transaction transaction = prepared.transaction();
        TransactionEntity entity = new TransactionEntity();
        entity.setId(transaction.id().value());
        entity.setType(transaction.type().name());
        entity.setStatus(transaction.status().name());
        entity.setIdempotencyKey(transaction.idempotencyKey());
        entity.setReversesTransactionId(transaction.reverses() == null ? null : transaction.reverses().value());
        entity.setRequestedAt(transaction.requestedAt());
        entity.setPostedAt(transaction.postedAt());
        entity.setDescription(transaction.description());
        for (LedgerEntry entry : transaction.entries()) {
            LedgerEntryEntity row = new LedgerEntryEntity();
            row.setId(entry.id());
            row.setTransaction(entity);
            row.setAccountId(entry.accountId().value());
            row.setDirection(entry.direction().name());
            row.setAmountMinor(entry.amount().amountMinor());
            row.setCurrency(entry.amount().currency().name());
            row.setBookedAt(entry.bookedAt());
            row.setSeq(entry.sequence());
            entity.getEntries().add(row);
        }
        try {
            em.persist(entity);
            for (Map.Entry<AccountId, Long> change : prepared.deltas().entrySet()) {
                AccountEntity account = locked.get(change.getKey().value());
                account.setCachedBalanceMinor(Math.addExact(account.getCachedBalanceMinor(), change.getValue()));
            }
            em.flush();
            em.createNativeQuery("""
                            INSERT INTO outbox (id, aggregate_id, event_type, payload, created_at)
                            VALUES (?1, ?2, 'POSTED', ?3, ?4)
                            """)
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, transaction.id().value())
                    .setParameter(3, transaction.type().name() + "|" + transaction.id())
                    .setParameter(4, java.sql.Timestamp.from(
                            transaction.postedAt() == null ? Instant.now() : transaction.postedAt()))
                    .executeUpdate();
        } catch (PersistenceException ex) {
            if (key != null && isUniqueViolation(ex)) {
                throw new IdempotencyRace(key);
            }
            throw ex;
        }
        if (beforeCommit != null) {
            beforeCommit.run();
        }
        return transaction;
    }

    private Map<UUID, AccountEntity> lockInIdOrder(List<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            throw new DomainException(ErrorCode.UNBALANCED_TRANSACTION, "a posting needs at least two legs");
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < accountIds.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append('?').append(i + 1);
        }
        var query = em.createNativeQuery(
                "SELECT id FROM accounts WHERE id IN (" + placeholders + ") ORDER BY id FOR UPDATE");
        for (int i = 0; i < accountIds.size(); i++) {
            query.setParameter(i + 1, accountIds.get(i));
        }
        query.getResultList();
        Map<UUID, AccountEntity> locked = new HashMap<>();
        for (UUID id : accountIds) {
            AccountEntity entity = em.find(AccountEntity.class, id);
            if (entity == null) {
                throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id);
            }
            locked.put(id, entity);
        }
        return locked;
    }

    private Transaction findByKey(String key) {
        List<TransactionEntity> rows = em.createQuery("""
                        select distinct t from TransactionEntity t
                        left join fetch t.entries
                        where t.idempotencyKey = :key
                        """, TransactionEntity.class)
                .setParameter("key", key)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        return LedgerMapper.toTransaction(rows.get(0));
    }

    private Transaction loadWinner(String key) {
        Transaction winner = transactions.execute(status -> findByKey(key));
        if (winner == null) {
            throw new DomainException(ErrorCode.DUPLICATE, "idempotency key " + key);
        }
        return winner;
    }

    private static boolean isUniqueViolation(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("duplicate key")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isOptimistic(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("OptimisticLock")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String blankToNull(String key) {
        return key == null || key.isBlank() ? null : key;
    }
}
