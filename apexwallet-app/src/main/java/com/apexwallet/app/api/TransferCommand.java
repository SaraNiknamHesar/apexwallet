package com.apexwallet.app.api;

import com.apexwallet.app.crypto.CanonicalHash;

import java.util.UUID;

public record TransferCommand(UUID fromAccountId, UUID toAccountId, long amountMinor, String currency, String description) {

    public String hash() {
        return CanonicalHash.transfer(fromAccountId, toAccountId, amountMinor, currency, description);
    }
}
