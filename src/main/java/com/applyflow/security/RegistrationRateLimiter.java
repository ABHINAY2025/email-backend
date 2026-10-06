package com.applyflow.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sliding-window limit for registrations: at most 5 per client IP per hour (like the login tracker). */
@Component
public class RegistrationRateLimiter {

    static final int MAX_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public RegistrationRateLimiter() {
        this(Clock.systemUTC());
    }

    RegistrationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records an attempt for {@code key}; returns false (and records nothing) when the limit is reached. */
    public boolean tryAcquire(String key) {
        Deque<Instant> q = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            Instant now = clock.instant();
            Instant cutoff = now.minus(WINDOW);
            while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) {
                q.pollFirst();
            }
            if (q.size() >= MAX_PER_WINDOW) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
