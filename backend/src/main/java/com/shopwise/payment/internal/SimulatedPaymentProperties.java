package com.shopwise.payment.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param declineAll make every payment fail, to exercise error paths
 */
@ConfigurationProperties("shopwise.payment.simulated")
public record SimulatedPaymentProperties(boolean declineAll) {
}
