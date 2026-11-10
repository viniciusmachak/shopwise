package com.shopwise.catalog;

import org.jspecify.annotations.Nullable;

public record ProductSummary(
        long id,
        String slug,
        String name,
        String brand,
        String category,
        Money price,
        @Nullable String imageUrl,
        boolean inStock) {
}
