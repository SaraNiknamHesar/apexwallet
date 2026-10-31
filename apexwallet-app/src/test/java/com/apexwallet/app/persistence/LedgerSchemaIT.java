package com.apexwallet.app.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerSchemaIT {

    static boolean dockerUp() {
        try {
            org.testcontainers.DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @Test
    @EnabledIf("dockerUp")
    void flywaySchemaRejectsLedgerMutationAndReconciles() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load()
                    .migrate();
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                UUID cash = UUID.randomUUID();
                UUID customer = UUID.randomUUID();
                UUID tx = UUID.randomUUID();
                insertAccount(connection, cash, "bank", "BANK_CASH", 100);
                insertAccount(connection, customer, "alice", "CUSTOMER_LIABILITY", 100);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO transactions (id, type, status, requested_at, posted_at, description)
                        VALUES (?, 'DEPOSIT', 'POSTED', now(), now(), 'opening')
                        """)) {
                    statement.setObject(1, tx);
                    statement.executeUpdate();
                }
                insertEntry(connection, UUID.randomUUID(), tx, cash, "DEBIT", 1);
                insertEntry(connection, UUID.randomUUID(), tx, customer, "CREDIT", 2);

                SQLException update = assertThrows(SQLException.class, () -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("UPDATE ledger_entries SET amount_minor = 1");
                    }
                });
                assertTrue(update.getMessage().contains("append-only"));

                SQLException delete = assertThrows(SQLException.class, () -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("DELETE FROM ledger_entries");
                    }
                });
                assertTrue(delete.getMessage().contains("append-only"));

                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("""
                             SELECT count(*) FROM account_reconciliation
                             WHERE cached_balance_minor <> folded_balance_minor
                             """)) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO audit_log (id, actor, action, at, detail) VALUES (?, 'auditor', 'POST', now(), 'ok')
                        """)) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.executeUpdate();
                }
                SQLException auditUpdate = assertThrows(SQLException.class, () -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("UPDATE audit_log SET detail = 'tampered'");
                    }
                });
                assertTrue(auditUpdate.getMessage().contains("append-only"));
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT coalesce(sum(volume_minor), 0) FROM monthly_volume")) {
                    assertTrue(rows.next());
                    assertEquals(200, rows.getLong(1));
                }
            }
        }
    }

    private static void insertAccount(Connection connection, UUID id, String owner, String type, long balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO accounts (id, owner_ref, currency, status, account_type, cached_balance_minor, created_at)
                VALUES (?, ?, 'EUR', 'ACTIVE', ?, ?, now())
                """)) {
            statement.setObject(1, id);
            statement.setString(2, owner);
            statement.setString(3, type);
            statement.setLong(4, balance);
            statement.executeUpdate();
        }
    }

    private static void insertEntry(Connection connection, UUID id, UUID tx, UUID account, String direction, int seq) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger_entries
                    (id, transaction_id, account_id, direction, amount_minor, currency, booked_at, seq)
                VALUES (?, ?, ?, ?, 100, 'EUR', now(), ?)
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tx);
            statement.setObject(3, account);
            statement.setString(4, direction);
            statement.setInt(5, seq);
            statement.executeUpdate();
        }
    }
}
