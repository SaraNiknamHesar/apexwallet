package com.apexwallet.app.loan;

import java.util.UUID;

public record LoanView(
        UUID id,
        String borrowerUsername,
        UUID accountId,
        long principalMinor,
        long outstandingMinor,
        String currency,
        String status
) {
    static LoanView from(Loan loan) {
        return new LoanView(
                loan.id(),
                loan.borrowerUsername(),
                loan.accountId(),
                loan.principalMinor(),
                loan.outstandingMinor(),
                loan.currency(),
                loan.status().name());
    }
}
