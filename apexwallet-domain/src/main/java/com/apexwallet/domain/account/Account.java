package com.apexwallet.domain.account;

import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.money.Currency;

import java.util.Objects;

/**
 * Immutable account. Balance is not a field: the ledger derives it from entries.
 * {@code externalAccountNumber} is plaintext inside the domain. Encryption happens at the persistence boundary.
 */
public record Account(
        AccountId id,
        String ownerRef,
        Currency currency,
        AccountType type,
        AccountStatus status,
        long overdraftLimitMinor,
        String externalAccountNumber
) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        if (ownerRef == null || ownerRef.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "ownerRef is required");
        }
        if (overdraftLimitMinor < 0) {
            throw new DomainException(ErrorCode.NEGATIVE_AMOUNT, "overdraft limit must be >= 0");
        }
        ownerRef = ownerRef.trim();
        externalAccountNumber = externalAccountNumber == null ? null : externalAccountNumber.trim();
    }

    public static Account open(String ownerRef, Currency currency, AccountType type) {
        return new Account(AccountId.random(), ownerRef, currency, type, AccountStatus.ACTIVE, 0, null);
    }

    public Account withStatus(AccountStatus next) {
        return new Account(id, ownerRef, currency, type, next, overdraftLimitMinor, externalAccountNumber);
    }

    public Account withOverdraft(long nextLimit) {
        return new Account(id, ownerRef, currency, type, status, nextLimit, externalAccountNumber);
    }

    public Account withExternalNumber(String number) {
        return new Account(id, ownerRef, currency, type, status, overdraftLimitMinor, number);
    }
}
