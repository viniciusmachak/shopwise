package com.shopwise.payment.internal;

import java.time.Instant;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;

/**
 * Calls Stripe's API with the official client.
 */
class StripeClientCheckout implements StripeCheckout {

    private final StripeClient client;

    StripeClientCheckout(String secretKey) {
        this.client = new StripeClient(secretKey);
    }

    @Override
    public Session createSession(long orderId, long amountInCents, String currency, String description,
            String successUrl, String cancelUrl, Instant expiresAt) {
        var params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setClientReferenceId(String.valueOf(orderId))
                .putMetadata("orderId", String.valueOf(orderId))
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setExpiresAt(expiresAt.getEpochSecond())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency.toLowerCase())
                                .setUnitAmount(amountInCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(description)
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            return toSession(client.v1().checkout().sessions().create(params));
        }
        catch (StripeException e) {
            throw new IllegalStateException("Stripe could not create the checkout: " + e.getMessage(), e);
        }
    }

    @Override
    public Session retrieveSession(String sessionId) {
        try {
            return toSession(client.v1().checkout().sessions().retrieve(sessionId));
        }
        catch (StripeException e) {
            throw new IllegalStateException("Stripe could not find checkout " + sessionId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void refund(String paymentIntentId) {
        try {
            client.v1().refunds().create(RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build());
        }
        catch (StripeException e) {
            throw new IllegalStateException("Stripe could not refund " + paymentIntentId + ": " + e.getMessage(), e);
        }
    }

    private static Session toSession(com.stripe.model.checkout.Session s) {
        return new Session(s.getId(), s.getUrl(), s.getPaymentStatus(), s.getStatus(), s.getPaymentIntent());
    }
}
