package com.shopwise.payment;

/**
 * Public API of the payment module.
 */
public interface PaymentService {

    /** Starts paying for an order. Runs inside the caller's transaction. */
    PaymentStart start(PaymentRequest request);

    /** Refunds every completed payment of the order. */
    void refund(long orderId);

    /**
     * Asks the provider about a payment that should have finished by now, in case its
     * notification never arrived. Publishes {@link PaymentConfirmed} or {@link PaymentExpired}.
     */
    void reconcile(long orderId);
}
