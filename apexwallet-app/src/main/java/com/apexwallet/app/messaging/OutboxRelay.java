package com.apexwallet.app.messaging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class OutboxRelay {

    private OutboxRelay() {
    }

    public static int publish(OutboxQueue store, Consumer<OutboxMessage> sink, int maxAttempts, List<OutboxMessage> deadLetters) {
        int sent = 0;
        for (OutboxMessage message : store.unpublished()) {
            try {
                sink.accept(message);
                store.markPublished(message.id());
                sent++;
            } catch (RuntimeException ex) {
                int attempts = store.attempt(message.id());
                if (attempts >= maxAttempts) {
                    store.deadLetter(message.id());
                    deadLetters.add(message);
                }
            }
        }
        return sent;
    }
}
