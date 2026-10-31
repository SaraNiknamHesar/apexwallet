package com.apexwallet.app.persistence;

import com.apexwallet.app.loan.Loan;
import com.apexwallet.app.loan.LoanStatus;
import com.apexwallet.app.loan.LoanStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("postgres")
public class JdbcLoanStore implements LoanStore {

    private final JdbcTemplate jdbc;

    public JdbcLoanStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Loan loan) {
        jdbc.update("""
                INSERT INTO loans
                    (id, borrower_id, borrower_username, account_id, principal_minor, outstanding_minor,
                     currency, status, checker_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    outstanding_minor = excluded.outstanding_minor,
                    status = excluded.status,
                    checker_id = excluded.checker_id
                """,
                loan.id(),
                loan.borrowerId(),
                loan.borrowerUsername(),
                loan.accountId(),
                loan.principalMinor(),
                loan.outstandingMinor(),
                loan.currency(),
                loan.status().name(),
                loan.checkerId(),
                SqlTimes.timestamp(Instant.now()));
    }

    @Override
    public Optional<Loan> find(UUID id) {
        return jdbc.query("select * from loans where id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public List<Loan> findByBorrower(UUID borrowerId) {
        return jdbc.query("select * from loans where borrower_id = ? order by created_at desc", this::map, borrowerId);
    }

    @Override
    public List<Loan> all() {
        return jdbc.query("select * from loans order by created_at desc", this::map);
    }

    private Loan map(ResultSet row, int ignored) throws SQLException {
        UUID checker = row.getObject("checker_id", UUID.class);
        return new Loan(
                row.getObject("id", UUID.class),
                row.getObject("borrower_id", UUID.class),
                row.getString("borrower_username"),
                row.getObject("account_id", UUID.class),
                row.getLong("principal_minor"),
                row.getLong("outstanding_minor"),
                row.getString("currency"),
                LoanStatus.valueOf(row.getString("status")),
                checker);
    }
}
