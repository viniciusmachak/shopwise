package com.shopwise.order;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * An order moved forward in fulfilment (shipped or delivered).
 */
@DomainEvent
public record OrderStatusChanged(long orderId, long customerId, OrderStatus status) {
}
