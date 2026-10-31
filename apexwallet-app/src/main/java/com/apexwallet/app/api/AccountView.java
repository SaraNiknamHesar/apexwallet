package com.apexwallet.app.api;

import java.util.UUID;

public record AccountView(
        UUID id,
        String ownerRef,
        String currency,
        String type,
        String status,
        long balanceMinor,
        String externalAccountNumber,
        long version
) {
}
