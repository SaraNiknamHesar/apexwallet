package com.apexwallet.app.audit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Profile("local")
public class MemoryAuditSink implements AuditSink {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(String action, String actor, Instant at, String correlationId) {
        events.add(new AuditEvent(action, actor, at, correlationId));
    }

    @Override
    public List<AuditEvent> events() {
        return List.copyOf(events);
    }
}
