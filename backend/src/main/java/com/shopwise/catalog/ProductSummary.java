package com.shopwise.catalog;

public record ProductSummary(
        long id,
        String slug,
        String name,
        String brand,
        String category,
        Money price,
        String imageUrl,
        boolean inStock) {
}
