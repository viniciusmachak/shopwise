package com.shopwise.payment.internal;

import java.time.Clock;

import com.shopwise.payment.PaymentRequest;
import com.shopwise.payment.PaymentStart;

/**
 * Stripe Checkout: the customer pays on Stripe's page, so card data never reaches Shopwise.
 */
class StripePaymentGateway implements PaymentGateway {

    private final StripeCheckout stripe;
    private final StripeProperties properties;
    private final Clock clock;

    StripePaymentGateway(StripeCheckout stripe, StripeProperties properties, Clock clock) {
        this.stripe = stripe;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public Started start(PaymentRequest request) {
        var expiresAt = clock.instant().plus(properties.checkoutTtl());
        var cents = request.amount().amount().movePointRight(2).longValueExact();
        var session = stripe.createSession(request.orderId(), cents, request.amount().currency(), request.description(),
                request.successUrl(), request.cancelUrl(), expiresAt);
        return new Started(new PaymentStart.Redirect(session.url(), expiresAt), session.id());
    }

    @Override
    public Status status(String reference) {
        var session = stripe.retrieveSession(reference);
        return new Status("paid".equals(session.paymentStatus()), "open".equals(session.status()), session.paymentIntent());
    }

    @Override
    public void refund(String providerPaymentId) {
        stripe.refund(providerPaymentId);
    }
}
