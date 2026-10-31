package com.apexwallet.app.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @Positive Long amountMinor,
        @NotBlank String currency,
        @Size(max = 200) String description
) {
    public TransferCommand toCommand() {
        return new TransferCommand(fromAccountId, toAccountId, amountMinor, currency, description);
    }
}
