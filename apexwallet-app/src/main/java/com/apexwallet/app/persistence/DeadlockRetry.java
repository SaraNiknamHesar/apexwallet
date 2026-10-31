package com.apexwallet.app.persistence;

/** Retries a deadlock-shaped failure with bounded backoff. The database is what detects the deadlock. */
public final class DeadlockRetry {

    private DeadlockRetry() {
    }

    public static <T> T run(int attempts, java.util.function.Supplier<T> action, java.util.function.Predicate<RuntimeException> retryable) {
        RuntimeException last = null;
        long sleepMs = 5;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                last = ex;
                if (!retryable.test(ex) || attempt == attempts) {
                    throw ex;
                }
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
                sleepMs = Math.min(sleepMs * 2, 200);
            }
        }
        throw last;
    }

    public static boolean isDeadlock(RuntimeException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql && "40P01".equals(sql.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("40P01") || message.contains("deadlock detected"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
