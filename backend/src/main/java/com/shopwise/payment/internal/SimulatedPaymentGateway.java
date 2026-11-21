package com.shopwise.payment.internal;

import java.util.UUID;

import com.shopwise.payment.PaymentRequest;
import com.shopwise.payment.PaymentStart;

/**
 * Approves at once (or declines everything when configured to), without a redirect.
 */
class SimulatedPaymentGateway implements PaymentGateway {

    private final SimulatedPaymentProperties properties;

    SimulatedPaymentGateway(SimulatedPaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public Started start(PaymentRequest request) {
        var reference = "sim_" + UUID.randomUUID();
        return new Started(properties.declineAll()
                ? new PaymentStart.Declined("The payment was declined.")
                : new PaymentStart.Paid(reference), reference);
    }

    @Override
    public Status status(String reference) {
        return new Status(true, false, reference);
    }

    @Override
    public void refund(String providerPaymentId) {
        // Nothing to do for simulated payments.
    }
}
