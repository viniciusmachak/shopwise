package com.shopwise.payment.internal;

import java.sql.Timestamp;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.payment.PaymentConfirmed;
import com.shopwise.payment.PaymentExpired;
import com.shopwise.payment.PaymentRequest;
import com.shopwise.payment.PaymentService;
import com.shopwise.payment.PaymentStart;

@Service
@Transactional
class DefaultPaymentService implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentService.class);

    private final PaymentGateway gateway;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    DefaultPaymentService(PaymentGateway gateway, JdbcClient jdbc, ApplicationEventPublisher events) {
        this.gateway = gateway;
        this.jdbc = jdbc;
        this.events = events;
    }

    @Override
    public PaymentStart start(PaymentRequest request) {
        var started = gateway.start(request);
        var status = switch (started.result()) {
            case PaymentStart.Paid p -> "APPROVED";
            case PaymentStart.Declined d -> "DECLINED";
            case PaymentStart.Redirect r -> "PENDING";
        };
        jdbc.sql("""
                insert into payment (order_id, amount, currency, status, reference, provider, provider_payment_id,
                                     checkout_url, expires_at)
                values (:order, :amount, :currency, :status, :ref, :provider, :providerId, :url, :expires)
                """)
                .param("order", request.orderId())
                .param("amount", request.amount().amount())
                .param("currency", request.amount().currency())
                .param("status", status)
                .param("ref", started.reference())
                .param("provider", gateway.name())
                .param("providerId", started.result() instanceof PaymentStart.Paid p ? p.reference() : null)
                .param("url", started.result() instanceof PaymentStart.Redirect r ? r.url() : null)
                .param("expires", started.result() instanceof PaymentStart.Redirect r ? Timestamp.from(r.expiresAt()) : null)
                .update();
        return started.result();
    }

    @Override
    public void refund(long orderId) {
        var payments = jdbc.sql("select provider_payment_id from payment where order_id = :order and status = 'APPROVED'")
                .param("order", orderId)
                .query(String.class)
                .list();
        payments.stream().filter(id -> id != null).forEach(gateway::refund);
        jdbc.sql("update payment set status = 'REFUNDED', updated_at = now() where order_id = :order and status = 'APPROVED'")
                .param("order", orderId)
                .update();
    }

    @Override
    public void reconcile(long orderId) {
        pendingReference(orderId).ifPresent(reference -> {
            var status = gateway.status(reference);
            if (status.paid()) {
                markPaid(reference, status.providerPaymentId());
            }
            else if (!status.open()) {
                markExpired(reference);
            }
        });
    }

    /** Stripe told us the customer paid. Safe to call more than once. */
    void markPaid(String reference, String providerPaymentId) {
        var orderId = jdbc.sql("""
                update payment set status = 'APPROVED', provider_payment_id = :pid, updated_at = now()
                where reference = :ref and status = 'PENDING' returning order_id
                """)
                .param("pid", providerPaymentId)
                .param("ref", reference)
                .query(Long.class)
                .optional();
        orderId.ifPresentOrElse(id -> events.publishEvent(new PaymentConfirmed(id)),
                () -> log.info("Ignoring payment notification for {}: not pending", reference));
    }

    /** The checkout expired without payment. Safe to call more than once. */
    void markExpired(String reference) {
        jdbc.sql("update payment set status = 'EXPIRED', updated_at = now() where reference = :ref and status = 'PENDING' returning order_id")
                .param("ref", reference)
                .query(Long.class)
                .optional()
                .ifPresent(id -> events.publishEvent(new PaymentExpired(id)));
    }

    private Optional<String> pendingReference(long orderId) {
        return jdbc.sql("select reference from payment where order_id = :order and status = 'PENDING' order by id desc limit 1")
                .param("order", orderId)
                .query(String.class)
                .optional();
    }
}
