package com.shopwise.catalog;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record ProductUpdated(long productId) {
}
