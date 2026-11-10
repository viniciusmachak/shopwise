package com.shopwise.cart;

import org.jspecify.annotations.Nullable;

import java.util.List;

import com.shopwise.catalog.Money;

public record CartView(
        List<Line> items,
        int itemCount,
        Money subtotal,
        @Nullable String couponCode,
        Money discount,
        Money total) {

    public record Line(
            long itemId,
            long variantId,
            long productId,
            String productName,
            @Nullable String size,
            @Nullable String color,
            Money unitPrice,
            int quantity,
            Money lineTotal,
            boolean available) {
    }
}
