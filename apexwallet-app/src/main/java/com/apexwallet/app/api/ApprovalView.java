package com.apexwallet.app.api;

import java.util.UUID;

public record ApprovalView(
        UUID id,
        String makerUsername,
        UUID fromAccountId,
        UUID toAccountId,
        long amountMinor,
        String currency,
        String description,
        String status
) {
}
