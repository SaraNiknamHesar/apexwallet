package com.apexwallet.app.persistence;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.concurrent.atomic.AtomicInteger;

/** Counts SQL statements so a test can show an N+1 load and the join that removes it. */
public class SqlCounter implements StatementInspector {

    private static final AtomicInteger STATEMENTS = new AtomicInteger();

    public static int count() {
        return STATEMENTS.get();
    }

    public static void reset() {
        STATEMENTS.set(0);
    }

    @Override
    public String inspect(String sql) {
        if (sql != null && !sql.contains("flyway_schema_history")) {
            STATEMENTS.incrementAndGet();
        }
        return sql;
    }
}
