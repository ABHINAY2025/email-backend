package com.applyflow.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationRateLimiterTest {

    @Test
    void allowsFivePerIpPerHour() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(clock);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
        assertThat(limiter.tryAcquire("5.6.7.8")).as("other IPs are independent").isTrue();

        now.set(now.get().plus(Duration.ofMinutes(61)));
        assertThat(limiter.tryAcquire("1.2.3.4")).as("window slid").isTrue();
    }

    @Test
    void clientIpUsesFirstForwardedHop() {
        assertThat(ClientIp.resolve("203.0.113.7, 10.0.0.1", "10.0.0.2")).isEqualTo("203.0.113.7");
        assertThat(ClientIp.resolve(" 198.51.100.1 ", "10.0.0.2")).isEqualTo("198.51.100.1");
        assertThat(ClientIp.resolve(null, "10.0.0.2")).isEqualTo("10.0.0.2");
        assertThat(ClientIp.resolve("", "10.0.0.2")).isEqualTo("10.0.0.2");
    }
}
