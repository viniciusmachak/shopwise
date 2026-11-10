package com.shopwise.catalog;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record ProductDetail(
        long id,
        String slug,
        String name,
        String brand,
        String category,
        String description,
        Money price,
        Map<String, Object> attributes,
        @Nullable String imageUrl,
        List<VariantView> variants) {
}
