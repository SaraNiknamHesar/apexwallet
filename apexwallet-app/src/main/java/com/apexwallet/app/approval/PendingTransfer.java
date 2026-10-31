package com.apexwallet.app.approval;

import java.util.UUID;

public record PendingTransfer(
        UUID id,
        UUID makerId,
        String makerUsername,
        UUID fromAccountId,
        UUID toAccountId,
        long amountMinor,
        String currency,
        String description,
        ApprovalStatus status,
        UUID checkerId
) {
    public PendingTransfer decide(ApprovalStatus next, UUID checkerId) {
        return new PendingTransfer(id, makerId, makerUsername, fromAccountId, toAccountId, amountMinor, currency,
                description, next, checkerId);
    }
}
