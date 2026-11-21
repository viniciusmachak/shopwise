package com.shopwise.payment;

import com.shopwise.catalog.Money;

/**
 * @param successUrl where the provider sends the customer after paying
 * @param cancelUrl  where the provider sends the customer if they give up
 */
public record PaymentRequest(long orderId, Money amount, String description, String successUrl, String cancelUrl) {
}
