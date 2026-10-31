package com.apexwallet.app.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WindowRateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public synchronized boolean allow(String key, int limit, Duration window, Instant now) {
        Deque<Long> deque = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long cutoff = now.minus(window).toEpochMilli();
        while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
            deque.removeFirst();
        }
        if (deque.size() >= limit) {
            return false;
        }
        deque.addLast(now.toEpochMilli());
        return true;
    }
}
