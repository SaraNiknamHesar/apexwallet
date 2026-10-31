package com.apexwallet.app.audit;

import java.time.Instant;

public record AuditEvent(String action, String actor, Instant at, String correlationId) {
}
