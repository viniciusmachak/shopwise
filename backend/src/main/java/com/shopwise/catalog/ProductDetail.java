package com.shopwise.catalog;

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
        String imageUrl,
        List<VariantView> variants) {
}
