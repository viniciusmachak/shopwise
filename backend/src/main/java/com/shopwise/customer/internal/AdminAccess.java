package com.shopwise.customer.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Gives the admin role to the configured e-mails: existing accounts on startup, new ones
 * when they are created. The role is read at sign-in, so a promoted admin signs in again.
 */
@Component
class AdminAccess {

    private static final Logger log = LoggerFactory.getLogger(AdminAccess.class);

    private final AdminProperties properties;
    private final JdbcClient jdbc;

    AdminAccess(AdminProperties properties, JdbcClient jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    void promoteConfiguredAdmins() {
        if (properties.emails().isEmpty()) {
            return;
        }
        int promoted = jdbc.sql("update customer set role = 'ADMIN' where lower(email) in (:emails) and role <> 'ADMIN'")
                .param("emails", properties.emails())
                .update();
        if (promoted > 0) {
            log.info("Gave the admin role to {} configured account(s)", promoted);
        }
    }

    void applyTo(Customer customer) {
        if (properties.isAdmin(customer.getEmail())) {
            customer.makeAdmin();
        }
    }
}
