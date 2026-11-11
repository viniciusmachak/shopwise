package com.shopwise.order;

import java.util.List;
import java.util.Optional;

/**
 * Public API for a customer's orders.
 */
public interface OrderService {

    List<OrderSummary> list(long customerId);

    Optional<OrderView> find(long customerId, long orderId);

    /** Cancels the order, puts the items back in stock and refunds the payment. */
    OrderView cancel(long customerId, long orderId);
}
