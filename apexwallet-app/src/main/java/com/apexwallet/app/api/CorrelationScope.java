package com.apexwallet.app.api;

import org.slf4j.MDC;

import java.util.concurrent.Executor;

public final class CorrelationScope {

    private CorrelationScope() {
    }

    public static void run(Executor executor, Runnable task) {
        String correlationId = MDC.get("correlation_id");
        executor.execute(() -> {
            if (correlationId != null) {
                MDC.put("correlation_id", correlationId);
            }
            try {
                task.run();
            } finally {
                MDC.remove("correlation_id");
            }
        });
    }
}
