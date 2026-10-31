package com.apexwallet.app.persistence;

import com.apexwallet.app.approval.ApprovalStatus;
import com.apexwallet.app.approval.ApprovalStore;
import com.apexwallet.app.approval.PendingTransfer;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("postgres")
public class JdbcApprovalStore implements ApprovalStore {

    private final JdbcTemplate jdbc;

    public JdbcApprovalStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PendingTransfer transfer) {
        jdbc.update("""
                INSERT INTO pending_approvals (id, maker, status, payload, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET maker = excluded.maker, status = excluded.status, payload = excluded.payload
                """, transfer.id(), transfer.makerUsername(), transfer.status().name(), payload(transfer),
                SqlTimes.timestamp(Instant.now()));
    }

    @Override
    public Optional<PendingTransfer> find(UUID id) {
        return jdbc.query("select status, payload from pending_approvals where id = ?",
                (row, ignored) -> decode(id, row.getString("status"), row.getString("payload")), id).stream().findFirst();
    }

    @Override
    public List<PendingTransfer> pending() {
        return jdbc.query("""
                select id, status, payload from pending_approvals where status = 'PENDING' order by created_at
                """, (row, ignored) -> decode(row.getObject("id", UUID.class), row.getString("status"), row.getString("payload")));
    }

    private static String payload(PendingTransfer transfer) {
        return String.join("\n",
                transfer.makerId().toString(),
                encode(transfer.makerUsername()),
                transfer.fromAccountId().toString(),
                transfer.toAccountId().toString(),
                Long.toString(transfer.amountMinor()),
                transfer.currency(),
                encode(transfer.description()),
                transfer.checkerId() == null ? "" : transfer.checkerId().toString());
    }

    private static PendingTransfer decode(UUID id, String status, String payload) {
        String[] parts = payload.split("\n", -1);
        String checker = parts[7];
        return new PendingTransfer(
                id,
                UUID.fromString(parts[0]),
                decode(parts[1]),
                UUID.fromString(parts[2]),
                UUID.fromString(parts[3]),
                Long.parseLong(parts[4]),
                parts[5],
                decode(parts[6]),
                ApprovalStatus.valueOf(status),
                checker.isEmpty() ? null : UUID.fromString(checker));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
