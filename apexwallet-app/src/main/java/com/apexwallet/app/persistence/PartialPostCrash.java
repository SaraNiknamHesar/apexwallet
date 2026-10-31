package com.apexwallet.app.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Leaves a transaction open on purpose. The integration test kills this process
 * and checks that Postgres rolled the half-written posting back.
 */
public final class PartialPostCrash {

    private PartialPostCrash() {
    }

    public static void main(String[] args) throws Exception {
        String url = args[0];
        String user = args[1];
        String password = args[2];
        UUID cash = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        try (Connection setup = DriverManager.getConnection(url, user, password)) {
            insertAccount(setup, cash, "crash-bank", "BANK_CASH");
            insertAccount(setup, customer, "crash-customer", "CUSTOMER_LIABILITY");
        }
        Connection open = DriverManager.getConnection(url, user, password);
        open.setAutoCommit(false);
        try (PreparedStatement statement = open.prepareStatement("""
                INSERT INTO transactions (id, type, status, requested_at, posted_at, description)
                VALUES (?, 'DEPOSIT', 'POSTED', now(), now(), 'half written')
                """)) {
            statement.setObject(1, transactionId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = open.prepareStatement("""
                INSERT INTO ledger_entries
                    (id, transaction_id, account_id, direction, amount_minor, currency, booked_at, seq)
                VALUES (?, ?, ?, 'DEBIT', 100, 'EUR', now(), 1)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, transactionId);
            statement.setObject(3, cash);
            statement.executeUpdate();
        }
        System.out.println("READY " + transactionId);
        System.out.flush();
        Thread.sleep(120_000);
        open.commit();
        open.close();
    }

    private static void insertAccount(Connection connection, UUID id, String owner, String type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO accounts
                    (id, owner_ref, currency, status, account_type, cached_balance_minor, created_at)
                VALUES (?, ?, 'EUR', 'ACTIVE', ?, 0, now())
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, owner);
            statement.setString(3, type);
            statement.executeUpdate();
        }
    }
}
