package com.shopwise.payment.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for Stripe in tests: sessions live in memory and tests decide their outcome.
 */
public class FakeStripeCheckout implements StripeCheckout {

    private final AtomicInteger ids = new AtomicInteger();
    final Map<String, Session> sessions = new ConcurrentHashMap<>();
    final List<String> refunds = new CopyOnWriteArrayList<>();

    @Override
    public Session createSession(long orderId, long amountInCents, String currency, String description,
            String successUrl, String cancelUrl, Instant expiresAt) {
        var id = "cs_test_" + ids.incrementAndGet();
        var session = new Session(id, "https://checkout.stripe.test/pay/" + id, "unpaid", "open", null);
        sessions.put(id, session);
        return session;
    }

    @Override
    public Session retrieveSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void refund(String paymentIntentId) {
        refunds.add(paymentIntentId);
    }

    void settle(String sessionId, boolean paid) {
        var s = sessions.get(sessionId);
        sessions.put(sessionId, paid
                ? new Session(s.id(), s.url(), "paid", "complete", "pi_" + sessionId)
                : new Session(s.id(), s.url(), "unpaid", "expired", null));
    }
}
