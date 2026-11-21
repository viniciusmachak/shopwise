package com.shopwise.order.internal;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopwise.payment.PaymentService;

/**
 * Safety net for missed payment notifications: asks the provider about checkouts that
 * ended a few minutes ago, which then confirms or releases the order.
 */
@Component
class PendingPaymentSweeper {

    static final Duration GRACE = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(PendingPaymentSweeper.class);

    private final DefaultOrderService orders;
    private final PaymentService payments;

    PendingPaymentSweeper(DefaultOrderService orders, PaymentService payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    void sweep() {
        for (var orderId : orders.overduePayments(Instant.now().minus(GRACE))) {
            try {
                payments.reconcile(orderId);
            }
            catch (RuntimeException e) {
                log.warn("Could not check the payment of order {}: {}", orderId, e.getMessage());
            }
        }
    }
}
