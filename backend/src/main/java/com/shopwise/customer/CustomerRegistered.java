package com.shopwise.customer;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record CustomerRegistered(long customerId) {
}
