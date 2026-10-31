package com.apexwallet.app.idempotency;

public record IdempotencyResult(int status, String body, boolean replay) {
}
