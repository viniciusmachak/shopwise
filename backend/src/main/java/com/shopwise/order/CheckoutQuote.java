package com.shopwise.order;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.shopwise.catalog.Money;
import com.shopwise.customer.AddressView;

/**
 * What the order would be if placed now. {@code cartHash} identifies the cart contents and
 * prices this quote was made from; placing the order with a stale hash fails.
 */
public record CheckoutQuote(
        List<QuoteLine> items,
        Money subtotal,
        @Nullable String couponCode,
        Money discount,
        ShippingOption shippingOption,
        Money shipping,
        Money total,
        List<ShippingChoice> shippingChoices,
        @Nullable AddressView address,
        String cartHash) {

    public record QuoteLine(String productName, @Nullable String size, int quantity, Money unitPrice, Money lineTotal) {
    }

    public record ShippingChoice(ShippingOption option, String label, Money price) {
    }
}
