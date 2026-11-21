package com.shopwise.payment.internal;

import java.time.Instant;

/**
 * The few Stripe calls we make, behind an interface so tests don't need the network.
 */
interface StripeCheckout {

    Session createSession(long orderId, long amountInCents, String currency, String description, String successUrl,
            String cancelUrl, Instant expiresAt);

    Session retrieveSession(String sessionId);

    void refund(String paymentIntentId);

    /** @param paymentStatus Stripe's payment_status ("paid", "unpaid", ...) and status ("open", "complete", "expired") */
    record Session(String id, String url, String paymentStatus, String status, String paymentIntent) {
    }
}
