package com.apexwallet.app.audit;

import java.time.Instant;
import java.util.List;

public interface AuditSink {

    void append(String action, String actor, Instant at, String correlationId);

    List<AuditEvent> events();
}
