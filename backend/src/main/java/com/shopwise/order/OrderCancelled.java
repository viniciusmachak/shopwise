package com.shopwise.order;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record OrderCancelled(long orderId, long customerId) {
}
