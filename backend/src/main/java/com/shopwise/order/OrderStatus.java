package com.shopwise.order;

public enum OrderStatus {
    /** Only exists while the payment is being taken, inside the checkout transaction. */
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
