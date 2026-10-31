package com.apexwallet.app.persistence;

import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountStatus;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.ledger.AccountStatement;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.LedgerEntry;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.PostingRules;
import com.apexwallet.domain.ledger.StatementLine;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionId;
import com.apexwallet.domain.ledger.TransactionStatus;
import com.apexwallet.domain.ledger.TransactionType;
import com.apexwallet.domain.money.Currency;
import com.apexwallet.domain.money.Money;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 4 ledger. One database transaction per posting, with account rows locked in id order.
 * The postgres profile's HTTP path uses {@link JpaBank}. This class is the JDBC version of the same rules.
 */
public final class JdbcLedgerRepository {

    private final DataSource dataSource;

    public JdbcLedgerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Account open(Account account) {
        return inTransaction(connection -> {
            if (findAccount(connection, account.id().value()).isPresent()) {
                throw new DomainException(ErrorCode.DUPLICATE, "account already open: " + account.id());
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO accounts
                        (id, owner_ref, currency, status, account_type, overdraft_limit_minor,
                         cached_balance_minor, version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?)
                    """)) {
                statement.setObject(1, account.id().value());
                statement.setString(2, account.ownerRef());
                statement.setString(3, account.currency().name());
                statement.setString(4, account.status().name());
                statement.setString(5, account.type().name());
                statement.setLong(6, account.overdraftLimitMinor());
                statement.setTimestamp(7, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
            return account.withExternalNumber(null);
        });
    }

    public Optional<Account> find(AccountId id) {
        return inTransaction(connection -> findAccount(connection, id.value()));
    }

    public List<Account> findByOwner(String ownerRef) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, owner_ref, currency, status, account_type, overdraft_limit_minor
                    FROM accounts WHERE owner_ref = ? ORDER BY id
                    """)) {
                statement.setString(1, ownerRef);
                try (ResultSet rows = statement.executeQuery()) {
                    List<Account> accounts = new ArrayList<>();
                    while (rows.next()) {
                        accounts.add(readAccount(rows));
                    }
                    return accounts;
                }
            }
        });
    }

    public Transaction post(Posting posting) {
        return post(posting, null);
    }

    /** {@code beforeCommit} runs after the writes and before commit. Tests use it to prove rollback. */
    public Transaction post(Posting posting, Runnable beforeCommit) {
        try {
            return DeadlockRetry.run(5,
                    () -> inTransaction(connection -> write(connection, posting, beforeCommit)),
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

    private Transaction loadWinner(String key) {
        return inTransaction(connection -> findByKey(connection, key)
                .orElseThrow(() -> new DomainException(ErrorCode.DUPLICATE, "idempotency key " + key)));
    }

    public Transaction reverse(TransactionId id, String description) {
        return DeadlockRetry.run(5, () -> inTransaction(connection -> {
            Transaction original = findTransaction(connection, id.value())
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "transaction not found: " + id));
            if (original.status() != TransactionStatus.POSTED) {
                throw new DomainException(ErrorCode.ILLEGAL_TRANSITION, "cannot reverse status " + original.status());
            }
            List<Posting.Leg> legs = new ArrayList<>();
            for (LedgerEntry entry : original.entries()) {
                legs.add(new Posting.Leg(entry.accountId(), entry.direction().opposite(), entry.amount()));
            }
            Transaction reversal = write(connection, new Posting(
                    TransactionId.random(), TransactionType.REVERSAL, legs,
                    description == null ? "reversal of " + id : description,
                    id, null, Instant.now()), null);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE transactions SET status = 'REVERSED' WHERE id = ? AND status = 'POSTED'")) {
                statement.setObject(1, id.value());
                if (statement.executeUpdate() != 1) {
                    throw new DomainException(ErrorCode.ILLEGAL_TRANSITION, "reverse lost the original status");
                }
            }
            return reversal;
        }), DeadlockRetry::isDeadlock);
    }

    public long balanceOf(AccountId id) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT cached_balance_minor FROM accounts WHERE id = ?")) {
                statement.setObject(1, id.value());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id);
                    }
                    return rows.getLong(1);
                }
            }
        });
    }

    public AccountStatement statement(AccountId id) {
        return inTransaction(connection -> {
            Account account = findAccount(connection, id.value())
                    .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id));
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT e.id, e.transaction_id, e.direction, e.amount_minor, e.booked_at, e.seq, t.description
                    FROM ledger_entries e
                    JOIN transactions t ON t.id = e.transaction_id
                    WHERE e.account_id = ? ORDER BY e.booked_at, e.seq
                    """)) {
                statement.setObject(1, id.value());
                try (ResultSet rows = statement.executeQuery()) {
                    List<StatementLine> lines = new ArrayList<>();
                    long running = 0;
                    while (rows.next()) {
                        Direction direction = Direction.valueOf(rows.getString("direction"));
                        long amount = rows.getLong("amount_minor");
                        running = Math.addExact(running, account.type().signedDelta(direction, amount));
                        lines.add(new StatementLine(
                                rows.getTimestamp("booked_at").toInstant(),
                                rows.getInt("seq"),
                                rows.getObject("id", UUID.class),
                                new TransactionId(rows.getObject("transaction_id", UUID.class)),
                                direction,
                                amount,
                                running,
                                rows.getString("description")));
                    }
                    return new AccountStatement(id, account.currency(), lines, running);
                }
            }
        });
    }

    public Account changeOverdraft(AccountId id, long newLimitMinor, long expectedVersion) {
        if (newLimitMinor < 0) {
            throw new DomainException(ErrorCode.NEGATIVE_AMOUNT, "overdraft limit must be >= 0");
        }
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE accounts SET overdraft_limit_minor = ?, version = version + 1
                    WHERE id = ? AND version = ?
                    """)) {
                statement.setLong(1, newLimitMinor);
                statement.setObject(2, id.value());
                statement.setLong(3, expectedVersion);
                if (statement.executeUpdate() == 1) {
                    return findAccount(connection, id.value()).orElseThrow();
                }
            }
            if (findAccount(connection, id.value()).isEmpty()) {
                throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id);
            }
            throw new DomainException(ErrorCode.OPTIMISTIC_LOCK, "version " + expectedVersion + " is stale");
        });
    }

    public long versionOf(AccountId id) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT version FROM accounts WHERE id = ?")) {
                statement.setObject(1, id.value());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + id);
                    }
                    return rows.getLong(1);
                }
            }
        });
    }

    private Transaction write(Connection connection, Posting posting, Runnable beforeCommit) throws SQLException {
        String key = blankToNull(posting.idempotencyKey());
        if (key != null) {
            Optional<Transaction> existing = findByKey(connection, key);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        List<UUID> accountIds = posting.legs().stream()
                .map(leg -> leg.accountId().value())
                .distinct()
                .sorted()
                .toList();
        Map<AccountId, Account> accounts = lockAccounts(connection, accountIds);
        Map<AccountId, Long> balances = new HashMap<>();
        for (UUID accountId : accountIds) {
            AccountId id = new AccountId(accountId);
            balances.put(id, balanceLocked(connection, accountId));
        }
        PostingRules.PreparedPosting prepared = PostingRules.prepare(posting, accounts, balances);
        Transaction transaction = prepared.transaction();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO transactions
                    (id, type, status, idempotency_key, reverses_transaction_id, requested_at, posted_at, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, transaction.id().value());
            statement.setString(2, transaction.type().name());
            statement.setString(3, transaction.status().name());
            statement.setString(4, transaction.idempotencyKey());
            if (transaction.reverses() == null) {
                statement.setObject(5, null);
            } else {
                statement.setObject(5, transaction.reverses().value());
            }
            statement.setTimestamp(6, Timestamp.from(transaction.requestedAt()));
            statement.setTimestamp(7, Timestamp.from(transaction.postedAt()));
            statement.setString(8, transaction.description());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger_entries
                    (id, transaction_id, account_id, direction, amount_minor, currency, booked_at, seq)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (LedgerEntry entry : transaction.entries()) {
                statement.setObject(1, entry.id());
                statement.setObject(2, entry.transactionId().value());
                statement.setObject(3, entry.accountId().value());
                statement.setString(4, entry.direction().name());
                statement.setLong(5, entry.amount().amountMinor());
                statement.setString(6, entry.amount().currency().name());
                statement.setTimestamp(7, Timestamp.from(entry.bookedAt()));
                statement.setInt(8, entry.sequence());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        for (Map.Entry<AccountId, Long> change : prepared.deltas().entrySet()) {
            long next = Math.addExact(balances.get(change.getKey()), change.getValue());
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE accounts SET cached_balance_minor = ?, version = version + 1 WHERE id = ?
                    """)) {
                statement.setLong(1, next);
                statement.setObject(2, change.getKey().value());
                if (statement.executeUpdate() != 1) {
                    throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account disappeared: " + change.getKey());
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO outbox (id, aggregate_id, event_type, payload, created_at)
                VALUES (?, ?, 'POSTED', ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, transaction.id().value());
            statement.setString(3, transaction.type().name() + "|" + transaction.id());
            statement.setTimestamp(4, Timestamp.from(transaction.postedAt()));
            statement.executeUpdate();
        }
        if (beforeCommit != null) {
            beforeCommit.run();
        }
        return transaction;
    }

    private Map<AccountId, Account> lockAccounts(Connection connection, List<UUID> accountIds) throws SQLException {
        if (accountIds.isEmpty()) {
            throw new DomainException(ErrorCode.UNBALANCED_TRANSACTION, "a posting needs at least two legs");
        }
        String placeholders = String.join(", ", accountIds.stream().map(id -> "?").toList());
        Map<AccountId, Account> accounts = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, owner_ref, currency, status, account_type, overdraft_limit_minor FROM accounts WHERE id IN ("
                        + placeholders + ") ORDER BY id FOR UPDATE")) {
            for (int i = 0; i < accountIds.size(); i++) {
                statement.setObject(i + 1, accountIds.get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Account account = readAccount(rows);
                    accounts.put(account.id(), account);
                }
            }
        }
        if (accounts.size() != accountIds.size()) {
            throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "one or more accounts were not found");
        }
        return accounts;
    }

    private static long balanceLocked(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT cached_balance_minor FROM accounts WHERE id = ?")) {
            statement.setObject(1, accountId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + accountId);
                }
                return rows.getLong(1);
            }
        }
    }

    private Optional<Account> findAccount(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, owner_ref, currency, status, account_type, overdraft_limit_minor
                FROM accounts WHERE id = ?
                """)) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(readAccount(rows));
            }
        }
    }

    private Optional<Transaction> findByKey(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM transactions WHERE idempotency_key = ?")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return findTransaction(connection, rows.getObject(1, UUID.class));
            }
        }
    }

    private Optional<Transaction> findTransaction(Connection connection, UUID id) throws SQLException {
        TransactionHeader header;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, type, status, idempotency_key, reverses_transaction_id, requested_at, posted_at, description
                FROM transactions WHERE id = ?
                """)) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                header = readHeader(rows);
            }
        }
        List<LedgerEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, transaction_id, account_id, direction, amount_minor, currency, booked_at, seq
                FROM ledger_entries WHERE transaction_id = ? ORDER BY seq
                """)) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(readEntry(rows));
                }
            }
        }
        entries.sort(Comparator.comparingInt(LedgerEntry::sequence));
        return Optional.of(new Transaction(
                header.id, header.type, header.status, entries, header.description,
                header.reverses, header.idempotencyKey, header.requestedAt, header.postedAt));
    }

    private static Account readAccount(ResultSet rows) throws SQLException {
        return new Account(
                new AccountId(rows.getObject("id", UUID.class)),
                rows.getString("owner_ref"),
                Currency.valueOf(rows.getString("currency").trim()),
                AccountType.valueOf(rows.getString("account_type")),
                AccountStatus.valueOf(rows.getString("status")),
                rows.getLong("overdraft_limit_minor"),
                null);
    }

    private static TransactionHeader readHeader(ResultSet rows) throws SQLException {
        UUID reverses = rows.getObject("reverses_transaction_id", UUID.class);
        return new TransactionHeader(
                new TransactionId(rows.getObject("id", UUID.class)),
                TransactionType.valueOf(rows.getString("type")),
                TransactionStatus.valueOf(rows.getString("status")),
                rows.getString("description"),
                reverses == null ? null : new TransactionId(reverses),
                rows.getString("idempotency_key"),
                rows.getTimestamp("requested_at").toInstant(),
                rows.getTimestamp("posted_at").toInstant());
    }

    private static LedgerEntry readEntry(ResultSet rows) throws SQLException {
        return new LedgerEntry(
                rows.getObject("id", UUID.class),
                new TransactionId(rows.getObject("transaction_id", UUID.class)),
                new AccountId(rows.getObject("account_id", UUID.class)),
                Direction.valueOf(rows.getString("direction")),
                Money.ofMinor(rows.getLong("amount_minor"), Currency.valueOf(rows.getString("currency").trim())),
                rows.getTimestamp("booked_at").toInstant(),
                rows.getInt("seq"));
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T value = work.apply(connection);
                connection.commit();
                return value;
            } catch (Throwable ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailed) {
                    ex.addSuppressed(rollbackFailed);
                }
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (ex instanceof SQLException sql) {
                    throw sqlFailure(sql);
                }
                throw new IllegalStateException(ex);
            }
        } catch (SQLException ex) {
            throw sqlFailure(ex);
        }
    }

    private static boolean isUniqueViolation(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static RuntimeException sqlFailure(SQLException ex) {
        if ("40P01".equals(ex.getSQLState())) {
            return new IllegalStateException("deadlock detected 40P01", ex);
        }
        return new IllegalStateException(ex.getMessage(), ex);
    }

    private static String blankToNull(String key) {
        return key == null || key.isBlank() ? null : key;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private record TransactionHeader(
            TransactionId id,
            TransactionType type,
            TransactionStatus status,
            String description,
            TransactionId reverses,
            String idempotencyKey,
            Instant requestedAt,
            Instant postedAt
    ) {
    }
}
