package com.shopwise.catalog;

import java.math.BigDecimal;

/**
 * Optional filters for listing products. Null fields are ignored.
 */
public record ProductFilter(
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock) {

    public static ProductFilter none() {
        return new ProductFilter(null, null, null, null);
    }
}
