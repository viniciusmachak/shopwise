package com.shopwise.cart;

import java.util.List;

import com.shopwise.catalog.Money;

public record CartView(
        List<Line> items,
        int itemCount,
        Money subtotal,
        String couponCode,
        Money discount,
        Money total) {

    public record Line(
            long itemId,
            long variantId,
            long productId,
            String productName,
            String size,
            String color,
            Money unitPrice,
            int quantity,
            Money lineTotal,
            boolean available) {
    }
}
