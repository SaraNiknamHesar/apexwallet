package com.apexwallet.domain.account;

import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;

/**
 * The optimistic-lock rule without JPA: a writer must present the version it read.
 * Hibernate's {@code @Version} is the same rule, enforced in SQL.
 */
public final class VersionedAccount {

    private final AccountId id;
    private long overdraftLimitMinor;
    private long version;

    public VersionedAccount(AccountId id, long overdraftLimitMinor) {
        this.id = id;
        this.overdraftLimitMinor = overdraftLimitMinor;
    }

    public AccountId id() {
        return id;
    }

    public long overdraftLimitMinor() {
        return overdraftLimitMinor;
    }

    public long version() {
        return version;
    }

    public void changeOverdraft(long newLimit, long expectedVersion) {
        if (newLimit < 0) {
            throw new DomainException(ErrorCode.NEGATIVE_AMOUNT, "overdraft limit must be >= 0");
        }
        if (expectedVersion != version) {
            throw new DomainException(ErrorCode.OPTIMISTIC_LOCK,
                    "version " + expectedVersion + " is stale; current is " + version);
        }
        this.overdraftLimitMinor = newLimit;
        this.version++;
    }
}
