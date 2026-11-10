package com.shopwise.search;

import java.math.BigDecimal;

/**
 * A free-text search with optional structured filters. Null filters are ignored.
 */
public record SearchQuery(
        String text,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock,
        int limit) {

    public SearchQuery {
        limit = Math.clamp(limit, 1, 50);
    }

    public static SearchQuery of(String text) {
        return new SearchQuery(text, null, null, null, null, 10);
    }
}
