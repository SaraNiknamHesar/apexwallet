package com.apexwallet.app.messaging;

public record OutboxMessage(String id, String accountKey, String payload, int attempts) {
}
