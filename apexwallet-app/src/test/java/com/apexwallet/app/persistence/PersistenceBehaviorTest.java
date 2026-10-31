package com.apexwallet.app.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceBehaviorTest {

    @Test
    void naiveLoadsGrowWithTransactionsWhileAJoinDoesNot() {
        StatementLoadProbe naive = new StatementLoadProbe();
        naive.load("transactions");
        for (int i = 0; i < 4; i++) {
            naive.load("entries-" + i);
        }
        StatementLoadProbe joined = new StatementLoadProbe();
        joined.load("entries-for-account");
        assertEquals(5, naive.loads());
        assertEquals(1, joined.loads());
    }

    @Test
    void deadlockRetryStopsAfterTheBound() {
        AtomicInteger attempts = new AtomicInteger();
        int result = DeadlockRetry.run(3, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("40P01");
            }
            return 7;
        }, ex -> ex.getMessage().contains("40P01"));
        assertEquals(7, result);
        assertEquals(3, attempts.get());
    }

    @Test
    void domainSourcesDoNotImportJpa() throws Exception {
        Path root = Path.of("..", "apexwallet-domain", "src", "main", "java");
        assertTrue(Files.isDirectory(root), root.toAbsolutePath().toString());
        try (var files = Files.walk(root)) {
            List<Path> hits = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("jakarta.persistence") || source.contains("javax.persistence");
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .toList();
            assertEquals(List.of(), hits);
        }
    }
}
