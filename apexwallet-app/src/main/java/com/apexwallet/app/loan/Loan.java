package com.apexwallet.app.loan;

import java.util.UUID;

public record Loan(
        UUID id,
        UUID borrowerId,
        String borrowerUsername,
        UUID accountId,
        long principalMinor,
        long outstandingMinor,
        String currency,
        LoanStatus status,
        UUID checkerId
) {
    public Loan withOutstanding(long outstanding, LoanStatus next, UUID checker) {
        return new Loan(id, borrowerId, borrowerUsername, accountId, principalMinor, outstanding, currency, next, checker);
    }
}
