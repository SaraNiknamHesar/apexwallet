package com.apexwallet.app.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
class AccountEntity {

    @Id
    private UUID id;

    @Column(name = "owner_ref", nullable = false, length = 64)
    private String ownerRef;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "account_type", nullable = false, length = 32)
    private String accountType;

    @Column(name = "overdraft_limit_minor", nullable = false)
    private long overdraftLimitMinor;

    @Column(name = "cached_balance_minor", nullable = false)
    private long cachedBalanceMinor;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getOwnerRef() {
        return ownerRef;
    }

    void setOwnerRef(String ownerRef) {
        this.ownerRef = ownerRef;
    }

    String getCurrency() {
        return currency;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getAccountType() {
        return accountType;
    }

    void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    long getOverdraftLimitMinor() {
        return overdraftLimitMinor;
    }

    void setOverdraftLimitMinor(long overdraftLimitMinor) {
        this.overdraftLimitMinor = overdraftLimitMinor;
    }

    long getCachedBalanceMinor() {
        return cachedBalanceMinor;
    }

    void setCachedBalanceMinor(long cachedBalanceMinor) {
        this.cachedBalanceMinor = cachedBalanceMinor;
    }

    Long getVersion() {
        return version;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
