package com.apexwallet.app.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
class LedgerEntryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 6)
    private String direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    @Column(name = "seq", nullable = false)
    private int seq;

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    TransactionEntity getTransaction() {
        return transaction;
    }

    void setTransaction(TransactionEntity transaction) {
        this.transaction = transaction;
    }

    UUID getAccountId() {
        return accountId;
    }

    void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    String getDirection() {
        return direction;
    }

    void setDirection(String direction) {
        this.direction = direction;
    }

    long getAmountMinor() {
        return amountMinor;
    }

    void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    String getCurrency() {
        return currency;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    Instant getBookedAt() {
        return bookedAt;
    }

    void setBookedAt(Instant bookedAt) {
        this.bookedAt = bookedAt;
    }

    int getSeq() {
        return seq;
    }

    void setSeq(int seq) {
        this.seq = seq;
    }
}
