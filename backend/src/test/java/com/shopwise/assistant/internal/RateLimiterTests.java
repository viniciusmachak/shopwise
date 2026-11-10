package com.shopwise.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RateLimiterTests {

    @Test
    void limitsMessagesPerMinute() {
        var limiter = new RateLimiter(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        var key = UUID.randomUUID();

        for (int i = 0; i < RateLimiter.MAX_MESSAGES_PER_MINUTE; i++) {
            assertThat(limiter.tryAcquire(key)).isTrue();
        }
        assertThat(limiter.tryAcquire(key)).isFalse();
        assertThat(limiter.tryAcquire(UUID.randomUUID())).isTrue();
    }
}
