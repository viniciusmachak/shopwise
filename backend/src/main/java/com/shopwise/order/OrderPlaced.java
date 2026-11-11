package com.shopwise.order;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record OrderPlaced(long orderId, long customerId) {
}
