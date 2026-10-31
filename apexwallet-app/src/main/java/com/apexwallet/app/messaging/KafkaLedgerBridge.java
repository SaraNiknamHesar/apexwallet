package com.apexwallet.app.messaging;

import com.apexwallet.app.config.ApexProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Publishes unpublished outbox rows and reads them back. Active only with the {@code kafka} profile,
 * so the local desk still starts when no broker is running.
 */
@Component
@Profile("kafka")
public class KafkaLedgerBridge implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(KafkaLedgerBridge.class);

    private final OutboxQueue queue;
    private final ApexProperties properties;
    private final IdempotentConsumer consumed = new IdempotentConsumer();
    private volatile boolean running;
    private Thread worker;

    public KafkaLedgerBridge(OutboxQueue queue, ApexProperties properties) {
        this.queue = queue;
        this.properties = properties;
    }

    public int consumedEffects() {
        return consumed.effects();
    }

    static int publishPending(OutboxQueue queue, Producer<String, String> producer, String topic) {
        return OutboxRelay.publish(queue, message -> {
            try {
                producer.send(new ProducerRecord<>(topic, message.id(), message.payload())).get(2, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("kafka send failed", ex);
            }
        }, 3, new ArrayList<>());
    }

    static void accept(IdempotentConsumer seen, Iterable<ConsumerRecord<String, String>> records) {
        for (ConsumerRecord<String, String> record : records) {
            seen.onEvent(record.key());
        }
    }

    @Override
    public void start() {
        running = true;
        worker = new Thread(this::loop, "apexwallet-kafka");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafkaBootstrapServers());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "2000");
        producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, "apexwallet-outbox");
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafkaBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "apexwallet-ledger");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);
             KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(properties.getKafkaTopic()));
            while (running) {
                try {
                    publishPending(queue, producer, properties.getKafkaTopic());
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                    accept(consumed, records);
                } catch (RuntimeException ex) {
                    log.warn("kafka relay waiting: {}", ex.toString());
                    sleep();
                }
            }
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
