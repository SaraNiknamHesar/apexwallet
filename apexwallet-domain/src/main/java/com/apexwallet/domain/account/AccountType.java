package com.apexwallet.domain.account;

public enum AccountType {
    /** Customer deposits. A credit increases the balance the bank owes the customer. */
    CUSTOMER_LIABILITY,
    /** Cash the bank holds. A debit increases the asset. */
    BANK_CASH,
    /** Fees the bank has earned. A credit increases income. */
    FEE_INCOME,
    /** Money customers still owe on a study loan. A debit increases the asset. */
    LOAN_RECEIVABLE;

    public boolean creditIncreasesBalance() {
        return this != BANK_CASH && this != LOAN_RECEIVABLE;
    }

    public long signedDelta(com.apexwallet.domain.ledger.Direction direction, long amountMinor) {
        boolean increase = direction == com.apexwallet.domain.ledger.Direction.CREDIT
                ? creditIncreasesBalance()
                : !creditIncreasesBalance();
        try {
            return increase ? amountMinor : Math.negateExact(amountMinor);
        } catch (ArithmeticException ex) {
            throw new com.apexwallet.domain.error.DomainException(
                    com.apexwallet.domain.error.ErrorCode.OVERFLOW, "balance delta overflow");
        }
    }
}
