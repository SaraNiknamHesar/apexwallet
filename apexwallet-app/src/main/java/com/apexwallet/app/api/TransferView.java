package com.apexwallet.app.api;

import java.util.UUID;

public record TransferView(UUID transactionId, String status, Long fromBalanceMinor, Long toBalanceMinor) {
}
