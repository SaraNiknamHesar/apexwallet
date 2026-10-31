package com.apexwallet.app.loan;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class MemoryLoanStore implements LoanStore {

    private final ConcurrentHashMap<UUID, Loan> rows = new ConcurrentHashMap<>();

    @Override
    public void save(Loan loan) {
        rows.put(loan.id(), loan);
    }

    @Override
    public Optional<Loan> find(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public List<Loan> findByBorrower(UUID borrowerId) {
        return rows.values().stream()
                .filter(loan -> loan.borrowerId().equals(borrowerId))
                .sorted(Comparator.comparing(Loan::id).reversed())
                .toList();
    }

    @Override
    public List<Loan> all() {
        List<Loan> loans = new ArrayList<>(rows.values());
        loans.sort(Comparator.comparing(Loan::id).reversed());
        return loans;
    }
}
