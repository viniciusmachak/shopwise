package com.shopwise.payment;

import java.time.Instant;

/**
 * How starting a payment went: done right away, declined, or waiting for the customer
 * on the provider's page (then {@link PaymentConfirmed} or {@link PaymentExpired} follows).
 */
public sealed interface PaymentStart {

    record Paid(String reference) implements PaymentStart {
    }

    record Declined(String reason) implements PaymentStart {
    }

    record Redirect(String url, Instant expiresAt) implements PaymentStart {
    }
}
