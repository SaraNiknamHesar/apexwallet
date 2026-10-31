package com.apexwallet.app.loan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanStore {

    void save(Loan loan);

    Optional<Loan> find(UUID id);

    List<Loan> findByBorrower(UUID borrowerId);

    List<Loan> all();
}
