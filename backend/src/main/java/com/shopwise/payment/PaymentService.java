package com.shopwise.payment;

import com.shopwise.catalog.Money;

/**
 * Public API of the payment module.
 */
public interface PaymentService {

    PaymentResult charge(long orderId, Money amount);

    /** Refunds every approved payment of the order. */
    void refund(long orderId);
}
