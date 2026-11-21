package com.shopwise.payment;

import org.jmolecules.event.annotation.DomainEvent;

/** A payment that waited for the customer was never completed. */
@DomainEvent
public record PaymentExpired(long orderId) {
}
