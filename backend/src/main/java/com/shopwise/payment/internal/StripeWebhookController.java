package com.shopwise.payment.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

/**
 * Stripe's notifications about checkouts. Only signed requests are accepted; CSRF doesn't
 * apply because Stripe's servers call it, not a browser.
 */
@RestController
class StripeWebhookController {

    static final String PATH = "/api/payments/stripe/webhook";

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeProperties properties;
    private final DefaultPaymentService payments;

    StripeWebhookController(StripeProperties properties, DefaultPaymentService payments) {
        this.properties = properties;
        this.payments = payments;
    }

    @PostMapping(PATH)
    ResponseEntity<Void> handle(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (!properties.enabled() || properties.webhookSecret() == null || properties.webhookSecret().isBlank()
                || signature == null) {
            return ResponseEntity.badRequest().build();
        }
        com.stripe.model.Event event;
        try {
            event = Webhook.constructEvent(payload, signature, properties.webhookSecret());
        }
        catch (SignatureVerificationException e) {
            log.warn("Rejected a Stripe webhook with a bad signature");
            return ResponseEntity.badRequest().build();
        }

        var deserializer = event.getDataObjectDeserializer();
        var object = deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            }
            catch (Exception e) {
                return null;
            }
        });
        if (!(object instanceof Session session)) {
            return ResponseEntity.ok().build();
        }
        switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                if ("paid".equals(session.getPaymentStatus())) {
                    payments.markPaid(session.getId(), session.getPaymentIntent());
                }
            }
            case "checkout.session.expired", "checkout.session.async_payment_failed" -> payments.markExpired(session.getId());
            default -> {
            }
        }
        return ResponseEntity.ok().build();
    }
}
