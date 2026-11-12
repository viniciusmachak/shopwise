package com.shopwise.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * A product as the backoffice sees it, archived or not.
 */
public record AdminProductView(
        long id,
        String slug,
        String name,
        String brand,
        String category,
        String description,
        BigDecimal price,
        Map<String, Object> attributes,
        @Nullable String imageUrl,
        boolean active,
        List<Variant> variants) {

    public record Variant(long id, String sku, @Nullable String size, @Nullable String color,
            @Nullable BigDecimal priceOverride, int stock) {
    }
}
