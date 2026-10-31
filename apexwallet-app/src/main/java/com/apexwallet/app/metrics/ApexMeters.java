package com.apexwallet.app.metrics;

import com.apexwallet.app.messaging.OutboxQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** Gauges Prometheus can scrape. Names match the study dashboard. */
@Component
public class ApexMeters implements MeterBinder {

    private final OutboxQueue outbox;

    public ApexMeters(OutboxQueue outbox) {
        this.outbox = outbox;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("apexwallet.outbox.unpublished", outbox, queue -> queue.unpublished().size())
                .register(registry);
        Gauge.builder("apexwallet.outbox.dead_letters", outbox, OutboxQueue::deadCount)
                .register(registry);
    }
}
