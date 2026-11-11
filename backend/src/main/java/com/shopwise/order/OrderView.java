package com.shopwise.order;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.shopwise.catalog.Money;

public record OrderView(
        long id,
        OrderStatus status,
        Instant placedAt,
        @Nullable Instant cancelledAt,
        List<Line> items,
        Money subtotal,
        @Nullable String couponCode,
        Money discount,
        ShippingOption shippingOption,
        Money shipping,
        Money total,
        ShippingAddress shippingAddress,
        boolean cancellable) {

    public record Line(long productId, String productName, @Nullable String size, @Nullable String color,
            int quantity, Money unitPrice, Money lineTotal) {
    }

    public record ShippingAddress(String recipient, String line1, @Nullable String line2, String city, String state,
            String postalCode, String country) {

        /** Street and city only, safe to show the assistant. */
        public String shortLabel() {
            return line1.replaceFirst("^\\d+\\s+", "") + ", " + city;
        }
    }
}
