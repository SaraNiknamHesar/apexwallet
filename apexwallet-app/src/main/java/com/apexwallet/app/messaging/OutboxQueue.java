package com.apexwallet.app.messaging;

import java.util.List;

/** Unpublished ledger events. The local profile keeps them in memory. Postgres keeps the same rows. */
public interface OutboxQueue {

    void add(OutboxMessage message);

    List<OutboxMessage> unpublished();

    void markPublished(String id);

    int attempt(String id);

    void deadLetter(String id);

    int deadCount();

    boolean isPublished(String id);
}
