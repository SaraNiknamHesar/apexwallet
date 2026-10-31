package com.apexwallet.app.messaging;

import java.util.LinkedHashSet;
import java.util.Set;

public final class IdempotentConsumer {

    private final Set<String> seen = new LinkedHashSet<>();
    private int effects;

    public void onEvent(String eventId) {
        if (seen.add(eventId)) {
            effects++;
        }
    }

    public int effects() {
        return effects;
    }
}
