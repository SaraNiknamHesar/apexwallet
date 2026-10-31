package com.apexwallet.app.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-process outbox. The database version is the row inserted in the same JDBC transaction as the post. */
@Component
@Profile("local")
public class MemoryOutbox implements OutboxQueue {

    private final LinkedHashMap<String, OutboxMessage> rows = new LinkedHashMap<>();

    public void add(OutboxMessage message) {
        rows.put(message.id(), message);
    }

    public List<OutboxMessage> unpublished() {
        List<OutboxMessage> pending = new ArrayList<>();
        for (OutboxMessage message : rows.values()) {
            if (message.attempts() >= 0 && rows.containsKey(message.id())) {
                pending.add(message);
            }
        }
        return pending.stream().filter(message -> !published.contains(message.id()) && !dead.contains(message.id())).toList();
    }

    private final java.util.Set<String> published = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> dead = new java.util.LinkedHashSet<>();

    public void markPublished(String id) {
        published.add(id);
    }

    public int attempt(String id) {
        OutboxMessage current = rows.get(id);
        OutboxMessage next = new OutboxMessage(current.id(), current.accountKey(), current.payload(), current.attempts() + 1);
        rows.put(id, next);
        return next.attempts();
    }

    public void deadLetter(String id) {
        dead.add(id);
    }

    @Override
    public int deadCount() {
        return dead.size();
    }

    public boolean isPublished(String id) {
        return published.contains(id);
    }
}
