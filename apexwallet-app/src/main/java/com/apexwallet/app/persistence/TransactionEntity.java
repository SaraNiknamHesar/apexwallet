package com.apexwallet.app.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transactions")
class TransactionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "reverses_transaction_id")
    private UUID reversesTransactionId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(nullable = false, length = 280)
    private String description;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("seq")
    @Fetch(FetchMode.SELECT)
    private List<LedgerEntryEntity> entries = new ArrayList<>();

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getType() {
        return type;
    }

    void setType(String type) {
        this.type = type;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    UUID getReversesTransactionId() {
        return reversesTransactionId;
    }

    void setReversesTransactionId(UUID reversesTransactionId) {
        this.reversesTransactionId = reversesTransactionId;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    Instant getPostedAt() {
        return postedAt;
    }

    void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    List<LedgerEntryEntity> getEntries() {
        return entries;
    }
}
