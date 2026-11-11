package com.shopwise.payment.internal;

import com.shopwise.catalog.Money;
import com.shopwise.payment.PaymentResult;

/**
 * The payment provider. The simulated one is used until Stripe test mode is added.
 */
interface PaymentGateway {

    PaymentResult charge(long orderId, Money amount);

    void refund(String reference);
}
