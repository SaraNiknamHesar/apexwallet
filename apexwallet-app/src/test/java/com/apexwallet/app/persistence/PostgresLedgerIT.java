package com.apexwallet.app.persistence;

import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionStatus;
import com.apexwallet.domain.ledger.TransactionType;
import com.apexwallet.domain.money.Currency;
import com.apexwallet.domain.money.Money;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = LedgerPersistenceConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.profiles.active=postgres,ledger-it",
                "spring.main.web-application-type=none",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=true",
                "spring.datasource.hikari.maximum-pool-size=24",
                "spring.datasource.hikari.connection-timeout=60000",
                "spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration,org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration,org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"
        })
@Testcontainers(disabledWithoutDocker = true)
class PostgresLedgerIT {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JpaBank jpa;
    @Autowired
    DataSource dataSource;
    @Autowired
    StatementLoader statements;

    @Test
    void jdbcAndJpaRollBackAPostThatFailsBeforeCommit() {
        JdbcLedgerRepository jdbc = new JdbcLedgerRepository(dataSource);
        Account jdbcCash = jdbc.open(Account.open(owner("jdbc-rollback-cash"), Currency.EUR, AccountType.BANK_CASH));
        Account jdbcCustomer = jdbc.open(Account.open(owner("jdbc-rollback"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        assertThrows(IllegalStateException.class,
                () -> jdbc.post(deposit(jdbcCash.id(), jdbcCustomer.id(), 25),
                        () -> {
                            throw new IllegalStateException("killed before commit");
                        }));
        assertEquals(0, jdbc.balanceOf(jdbcCustomer.id()));

        Account jpaCash = jpa.open(Account.open(owner("jpa-rollback-cash"), Currency.EUR, AccountType.BANK_CASH));
        Account jpaCustomer = jpa.open(Account.open(owner("jpa-rollback"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        assertThrows(IllegalStateException.class,
                () -> jpa.post(deposit(jpaCash.id(), jpaCustomer.id(), 25),
                        () -> {
                            throw new IllegalStateException("killed before commit");
                        }));
        assertEquals(0, jpa.balanceOf(jpaCustomer.id()));
    }

    @Test
    void killedJvmLeavesNoPartialPost() throws Exception {
        String java = System.getProperty("java.home") + "\\bin\\java.exe";
        Process process = new ProcessBuilder(
                java, "-cp", System.getProperty("java.class.path"),
                "com.apexwallet.app.persistence.PartialPostCrash",
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (Exception ignored) {
                // The process is killed on purpose.
            }
        });
        reader.setDaemon(true);
        reader.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline && !output.toString().contains("READY ")) {
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(50);
        }
        assertTrue(output.toString().contains("READY "), output.toString());
        String ready = output.toString().lines().filter(line -> line.startsWith("READY ")).findFirst().orElseThrow();
        UUID transactionId = UUID.fromString(ready.substring("READY ".length()).trim());
        process.destroyForcibly();
        assertTrue(process.waitFor(15, TimeUnit.SECONDS));
        assertEquals(0, count("SELECT count(*) FROM transactions WHERE id = ?", transactionId));
        assertEquals(0, count("SELECT count(*) FROM ledger_entries WHERE transaction_id = ?", transactionId));
    }

    @Test
    void oppositeLockOrderIsRejectedAndRetryStillPosts() throws Exception {
        JdbcLedgerRepository jdbc = new JdbcLedgerRepository(dataSource);
        Account cash = jdbc.open(Account.open(owner("dead-cash"), Currency.EUR, AccountType.BANK_CASH));
        Account alice = jdbc.open(Account.open(owner("dead-alice"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        jdbc.post(deposit(cash.id(), alice.id(), 100));
        UUID low = cash.id().value().compareTo(alice.id().value()) < 0 ? cash.id().value() : alice.id().value();
        UUID high = low.equals(cash.id().value()) ? alice.id().value() : cash.id().value();
        CountDownLatch lowLocked = new CountDownLatch(1);
        CountDownLatch highLocked = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<SQLException> left = pool.submit(() -> lockPair(low, high, lowLocked, highLocked));
            Future<SQLException> right = pool.submit(() -> lockPair(high, low, highLocked, lowLocked));
            SQLException first = left.get(20, TimeUnit.SECONDS);
            SQLException second = right.get(20, TimeUnit.SECONDS);
            SQLException deadlock = "40P01".equals(sqlState(first)) ? first : second;
            assertEquals("40P01", sqlState(deadlock));
            AtomicInteger attempts = new AtomicInteger();
            Transaction posted = DeadlockRetry.run(3, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new IllegalStateException("deadlock detected", deadlock);
                }
                return jdbc.post(transfer(alice.id(), cash.id(), 1, "dead-retry"));
            }, DeadlockRetry::isDeadlock);
            assertEquals(TransactionStatus.POSTED, posted.status());
            assertEquals(1, attempts.get() - 1);
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void fiveHundredOrderedTransfersKeepTheInvariantOnJdbcAndJpa() throws Exception {
        assertFiveHundred(jdbcPoster(new JdbcLedgerRepository(dataSource)), "jdbc500");
        assertFiveHundred(jpaPoster(), "jpa500");
    }

    @Test
    void sameIdempotencyKeyPostsOnce() {
        JdbcLedgerRepository jdbc = new JdbcLedgerRepository(dataSource);
        Account cash = jdbc.open(Account.open(owner("idem-cash"), Currency.EUR, AccountType.BANK_CASH));
        Account alice = jdbc.open(Account.open(owner("idem-alice"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        Posting posting = deposit(cash.id(), alice.id(), 40, "once");
        Transaction first = jdbc.post(posting);
        Transaction second = jdbc.post(posting);
        assertEquals(first.id(), second.id());
        assertEquals(40, jdbc.balanceOf(alice.id()));

        Account jpaCash = jpa.open(Account.open(owner("idem-jpa-cash"), Currency.EUR, AccountType.BANK_CASH));
        Account jpaAlice = jpa.open(Account.open(owner("idem-jpa"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        Posting again = deposit(jpaCash.id(), jpaAlice.id(), 40, "jpa-once");
        assertEquals(jpa.post(again).id(), jpa.post(again).id());
        assertEquals(40, jpa.balanceOf(jpaAlice.id()));
    }

    @Test
    void joinedStatementLoadUsesFewerQueriesThanLazyLoads() {
        Account cash = jpa.open(Account.open(owner("nplus-cash"), Currency.EUR, AccountType.BANK_CASH));
        Account alice = jpa.open(Account.open(owner("nplus"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        AccountId cashId = cash.id();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(jpa.post(deposit(cashId, alice.id(), 1, "nplus-" + i)).id().value());
        }
        SqlCounter.reset();
        int lazy = statements.lazyLoads(ids);
        int joined = statements.joinedLoads(ids);
        assertTrue(lazy >= 5, "lazy queries=" + lazy);
        assertTrue(joined < lazy, "joined=" + joined + " lazy=" + lazy);
        assertTrue(joined <= 2, "joined queries=" + joined);
    }

    @Test
    void staleOverdraftVersionIsRejected() throws Exception {
        Account account = jpa.open(Account.open(owner("version"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        long version = jpa.versionOf(account.id());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> first = pool.submit(() -> change(account.id(), 10, version, start));
            Future<Object> second = pool.submit(() -> change(account.id(), 20, version, start));
            start.countDown();
            Object left = first.get(20, TimeUnit.SECONDS);
            Object right = second.get(20, TimeUnit.SECONDS);
            List<Object> results = List.of(left, right);
            assertTrue(results.contains("ok"), results.toString());
            assertTrue(results.contains(ErrorCode.OPTIMISTIC_LOCK), results.toString());
        }
    }

    @Test
    void indexServesTheStatementOrderAndAFullPoolRejectsTheNextCaller() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP INDEX ledger_entries_account_booked_idx");
                String before = explain(connection);
                assertTrue(before.toLowerCase().contains("seq scan"), before);
                statement.execute("""
                        CREATE INDEX ledger_entries_account_booked_idx
                        ON ledger_entries (account_id, booked_at, seq)
                        """);
                statement.execute("SET enable_seqscan = off");
                String after = explain(connection);
                assertTrue(after.contains("ledger_entries_account_booked_idx"), after);
            }
            connection.rollback();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1000);
        config.setPoolName("exhaustion-proof");
        try (HikariDataSource pool = new HikariDataSource(config); Connection held = pool.getConnection()) {
            SQLException exhausted = assertThrows(SQLException.class, pool::getConnection);
            assertTrue(exhausted.getMessage().toLowerCase().contains("connection is not available"), exhausted.getMessage());
            assertTrue(held.isValid(1));
        }
    }

    private void assertFiveHundred(Poster poster, String prefix) throws Exception {
        Account cash = poster.open(Account.open(owner(prefix + "-cash"), Currency.EUR, AccountType.BANK_CASH));
        Account alice = poster.open(Account.open(owner(prefix + "-alice"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        Account bob = poster.open(Account.open(owner(prefix + "-bob"), Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        poster.post(deposit(cash.id(), alice.id(), 50_000));
        poster.post(deposit(cash.id(), bob.id(), 50_000));
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            List<Future<Transaction>> futures = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                boolean toBob = i % 2 == 0;
                AccountId from = toBob ? alice.id() : bob.id();
                AccountId to = toBob ? bob.id() : alice.id();
                String key = prefix + "-t-" + i;
                futures.add(pool.submit(() -> poster.post(transfer(from, to, 1, key))));
            }
            for (Future<Transaction> future : futures) {
                assertEquals(TransactionStatus.POSTED, future.get(60, TimeUnit.SECONDS).status());
            }
        }
        assertEquals(50_000, poster.balance(alice.id()));
        assertEquals(50_000, poster.balance(bob.id()));
        assertEquals(100_000, poster.balance(cash.id()));
        assertEquals(0, mismatches(cash.id(), alice.id(), bob.id()));
        assertEquals(poster.balance(alice.id()), poster.statement(alice.id()).closingBalanceMinor());
    }

    private Object change(AccountId id, long limit, long version, CountDownLatch start) {
        try {
            start.await();
            jpa.changeOverdraft(id, limit, version);
            return "ok";
        } catch (DomainException ex) {
            return ex.code();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ex;
        }
    }

    private SQLException lockPair(UUID first, UUID second, CountDownLatch mine, CountDownLatch other) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            lock(connection, first);
            mine.countDown();
            if (!other.await(10, TimeUnit.SECONDS)) {
                connection.rollback();
                return new SQLException("partner did not lock");
            }
            lock(connection, second);
            connection.rollback();
            return null;
        } catch (SQLException ex) {
            return ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new SQLException("interrupted", ex);
        }
    }

    private static void lock(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM accounts WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("missing " + id);
                }
            }
        }
    }

    private static String explain(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     EXPLAIN SELECT id FROM ledger_entries
                     WHERE account_id = '00000000-0000-0000-0000-000000000001'
                     ORDER BY booked_at, seq
                     """)) {
            StringBuilder plan = new StringBuilder();
            while (rows.next()) {
                plan.append(rows.getString(1)).append('\n');
            }
            return plan.toString();
        }
    }

    private int mismatches(AccountId... ids) throws SQLException {
        String placeholders = String.join(", ", java.util.Collections.nCopies(ids.length, "?"));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT count(*) FROM account_reconciliation
                     WHERE id IN (%s) AND cached_balance_minor <> folded_balance_minor
                     """.formatted(placeholders))) {
            for (int i = 0; i < ids.length; i++) {
                statement.setObject(i + 1, ids[i].value());
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int count(String sql, UUID id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private Poster jdbcPoster(JdbcLedgerRepository repository) {
        return new Poster() {
            @Override
            public Account open(Account account) {
                return repository.open(account);
            }

            @Override
            public Transaction post(Posting posting) {
                return repository.post(posting);
            }

            @Override
            public long balance(AccountId id) {
                return repository.balanceOf(id);
            }

            @Override
            public com.apexwallet.domain.ledger.AccountStatement statement(AccountId id) {
                return repository.statement(id);
            }
        };
    }

    private Poster jpaPoster() {
        return new Poster() {
            @Override
            public Account open(Account account) {
                return jpa.open(account);
            }

            @Override
            public Transaction post(Posting posting) {
                return jpa.post(posting);
            }

            @Override
            public long balance(AccountId id) {
                return jpa.balanceOf(id);
            }

            @Override
            public com.apexwallet.domain.ledger.AccountStatement statement(AccountId id) {
                return jpa.statement(id);
            }
        };
    }

    private static Posting deposit(AccountId cash, AccountId customer, long amount) {
        return deposit(cash, customer, amount, null);
    }

    private static Posting deposit(AccountId cash, AccountId customer, long amount, String key) {
        if (amount == 0) {
            amount = 1;
        }
        return movement(TransactionType.DEPOSIT, cash, customer, amount, key);
    }

    private static Posting transfer(AccountId from, AccountId to, long amount, String key) {
        return movement(TransactionType.TRANSFER, from, to, amount, key);
    }

    private static Posting movement(TransactionType type, AccountId debit, AccountId credit, long amount, String key) {
        Money money = Money.ofMinor(amount, Currency.EUR);
        return new Posting(null, type, List.of(
                new Posting.Leg(debit, Direction.DEBIT, money),
                new Posting.Leg(credit, Direction.CREDIT, money)),
                type.name().toLowerCase(), null, key, null);
    }

    private static String owner(String name) {
        return name + "-" + UUID.randomUUID();
    }

    private static String sqlState(SQLException ex) {
        SQLException current = ex;
        while (current != null) {
            if (current.getSQLState() != null) {
                return current.getSQLState();
            }
            current = current.getNextException();
        }
        return null;
    }

    private interface Poster {
        Account open(Account account);

        Transaction post(Posting posting);

        long balance(AccountId id);

        com.apexwallet.domain.ledger.AccountStatement statement(AccountId id);
    }
}
