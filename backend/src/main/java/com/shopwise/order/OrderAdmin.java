package com.shopwise.order;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Public API for fulfilling orders from the backoffice.
 */
public interface OrderAdmin {

    /** Newest first, optionally by status and by order number or customer e-mail. */
    List<AdminOrderRow> list(@Nullable OrderStatus status, @Nullable String query);

    Optional<AdminOrderView> get(long orderId);

    /** PAID to SHIPPED. */
    AdminOrderView markShipped(long orderId, @Nullable String trackingNumber);

    /** SHIPPED to DELIVERED. */
    AdminOrderView markDelivered(long orderId);

    /** Cancels a paid order on the customer's behalf: restocks and refunds. */
    AdminOrderView cancel(long orderId);
}
