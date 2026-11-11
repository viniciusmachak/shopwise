package com.shopwise.notification.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param from sender shown in e-mails
 */
@ConfigurationProperties("shopwise.mail")
public record MailProperties(String from) {

    public MailProperties {
        from = from == null || from.isBlank() ? "Shopwise <no-reply@shopwise.local>" : from;
    }
}
