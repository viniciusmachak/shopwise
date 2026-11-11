package com.shopwise.customer.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Blocks sign-in for an e-mail after too many failed attempts in a short window. In memory.
 */
@Component
class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    boolean isBlocked(String email) {
        var window = failures.get(key(email));
        if (window == null) {
            return false;
        }
        synchronized (window) {
            prune(window);
            return window.size() >= MAX_FAILURES;
        }
    }

    void recordFailure(String email) {
        var window = failures.computeIfAbsent(key(email), k -> new ArrayDeque<>());
        synchronized (window) {
            prune(window);
            window.addLast(clock.millis());
        }
    }

    void recordSuccess(String email) {
        failures.remove(key(email));
    }

    private void prune(Deque<Long> window) {
        long now = clock.millis();
        while (!window.isEmpty() && now - window.peekFirst() > WINDOW.toMillis()) {
            window.pollFirst();
        }
    }

    private static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
