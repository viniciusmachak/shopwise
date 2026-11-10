package com.shopwise.assistant.internal;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Sliding-window limit on chat messages per cart, kept in memory.
 */
@Component
class RateLimiter {

    static final int MAX_MESSAGES_PER_MINUTE = 20;

    private final Map<UUID, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean tryAcquire(UUID key) {
        long now = clock.millis();
        var window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > 60_000) {
                window.pollFirst();
            }
            if (window.size() >= MAX_MESSAGES_PER_MINUTE) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
