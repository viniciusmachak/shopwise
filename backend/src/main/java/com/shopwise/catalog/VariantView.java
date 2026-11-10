package com.shopwise.catalog;

public record VariantView(
        long id,
        long productId,
        String productName,
        String sku,
        String size,
        String color,
        Money price,
        int stock) {
}
