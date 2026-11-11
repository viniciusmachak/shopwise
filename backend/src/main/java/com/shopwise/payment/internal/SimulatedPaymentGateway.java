package com.shopwise.payment.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shopwise.catalog.Money;
import com.shopwise.payment.PaymentResult;

@Component
class SimulatedPaymentGateway implements PaymentGateway {

    private final SimulatedPaymentProperties properties;

    SimulatedPaymentGateway(SimulatedPaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentResult charge(long orderId, Money amount) {
        var reference = "sim_" + UUID.randomUUID();
        if (properties.declineAll()) {
            return new PaymentResult(false, reference, "The payment was declined.");
        }
        return new PaymentResult(true, reference, null);
    }

    @Override
    public void refund(String reference) {
        // Nothing to do for simulated payments.
    }
}
