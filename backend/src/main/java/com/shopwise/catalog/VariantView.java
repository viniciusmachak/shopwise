package com.shopwise.catalog;

import org.jspecify.annotations.Nullable;

public record VariantView(
        long id,
        long productId,
        String productName,
        String sku,
        @Nullable String size,
        @Nullable String color,
        Money price,
        int stock) {
}
