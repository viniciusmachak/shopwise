package com.shopwise.payment.internal;

import com.shopwise.payment.PaymentRequest;
import com.shopwise.payment.PaymentStart;

/**
 * A payment provider: simulated, or Stripe Checkout.
 */
interface PaymentGateway {

    String name();

    /** For a redirect, {@code reference} is the provider's id for the pending payment. */
    Started start(PaymentRequest request);

    /** Status of a pending payment at the provider. */
    Status status(String reference);

    void refund(String providerPaymentId);

    record Started(PaymentStart result, String reference) {
    }

    record Status(boolean paid, boolean open, String providerPaymentId) {
    }
}
