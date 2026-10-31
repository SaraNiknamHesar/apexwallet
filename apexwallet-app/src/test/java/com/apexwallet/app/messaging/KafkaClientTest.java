package com.apexwallet.app.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaClientTest {

    @Test
    void publishSendsTheOutboxIdAndARepeatDoesNotApplyTwice() throws Exception {
        MemoryOutbox outbox = new MemoryOutbox();
        outbox.add(new OutboxMessage("evt-1", "acct", "POSTED|evt-1", 0));
        try (MockProducer<String, String> producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer())) {
            assertEquals(1, KafkaLedgerBridge.publishPending(outbox, producer, "apexwallet.ledger"));
            assertEquals("evt-1", producer.history().get(0).key());
            assertEquals("POSTED|evt-1", producer.history().get(0).value());
            assertEquals(0, KafkaLedgerBridge.publishPending(outbox, producer, "apexwallet.ledger"));
        }
        IdempotentConsumer seen = new IdempotentConsumer();
        List<ConsumerRecord<String, String>> records = List.of(new ConsumerRecord<>("apexwallet.ledger", 0, 0, "evt-1", "POSTED|evt-1"));
        KafkaLedgerBridge.accept(seen, records);
        KafkaLedgerBridge.accept(seen, records);
        assertEquals(1, seen.effects());
    }
}
