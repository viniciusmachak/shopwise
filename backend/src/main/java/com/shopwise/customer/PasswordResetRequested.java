package com.shopwise.customer;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * Carries a one-time reset link. Publications are deleted once handled.
 */
@DomainEvent
public record PasswordResetRequested(long customerId, String resetLink) {
}
