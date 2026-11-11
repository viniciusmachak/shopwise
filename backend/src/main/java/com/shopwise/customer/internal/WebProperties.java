package com.shopwise.customer.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param frontendUrl where the SPA is served, used for redirects and links in e-mails
 */
@ConfigurationProperties("shopwise.web")
public record WebProperties(String frontendUrl) {

    public WebProperties {
        frontendUrl = frontendUrl == null || frontendUrl.isBlank() ? "http://localhost:5173" : frontendUrl.replaceAll("/$", "");
    }
}
