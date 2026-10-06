package com.applyflow.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Simple in-memory brute-force protection for the login endpoint (per client IP). */
@Component
public class LoginAttemptTracker {

    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Deque<Instant> q = failures.get(key);
        if (q == null) {
            return false;
        }
        synchronized (q) {
            prune(q);
            return q.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(Instant.now());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private static void prune(Deque<Instant> q) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }
}
