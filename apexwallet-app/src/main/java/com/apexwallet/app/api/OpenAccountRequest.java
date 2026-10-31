package com.apexwallet.app.api;

import com.apexwallet.app.validation.Iban;
import jakarta.validation.constraints.NotBlank;

public record OpenAccountRequest(
        String ownerRef,
        @NotBlank String currency,
        @NotBlank String type,
        @Iban String externalAccountNumber
) {
}
