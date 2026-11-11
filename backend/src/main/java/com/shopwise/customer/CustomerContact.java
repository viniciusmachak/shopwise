package com.shopwise.customer;

/**
 * Contact details for sending a customer e-mail. Never pass these to the assistant.
 */
public record CustomerContact(long id, String email, String name) {
}
