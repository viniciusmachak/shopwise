package com.shopwise.payment;

import org.jmolecules.event.annotation.DomainEvent;

/** A payment that waited for the customer was completed. */
@DomainEvent
public record PaymentConfirmed(long orderId) {
}
