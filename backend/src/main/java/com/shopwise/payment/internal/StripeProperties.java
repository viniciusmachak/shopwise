package com.shopwise.payment.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe Checkout. Payments stay simulated unless {@code secretKey} is set.
 *
 * @param secretKey     sk_test_... (or sk_live_... when going live)
 * @param webhookSecret whsec_... from the webhook endpoint (or from `stripe listen` locally)
 * @param checkoutTtl   how long the customer has to pay before the order is released (30 minutes to 24 hours)
 */
@ConfigurationProperties("shopwise.payment.stripe")
public record StripeProperties(String secretKey, String webhookSecret, Duration checkoutTtl) {

    public StripeProperties {
        checkoutTtl = checkoutTtl == null ? Duration.ofMinutes(30) : checkoutTtl;
    }

    boolean enabled() {
        return secretKey != null && !secretKey.isBlank();
    }
}
