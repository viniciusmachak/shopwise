package com.shopwise.catalog;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Everything the search module needs to index a product.
 */
public record IndexableProduct(
        long id,
        String name,
        String brand,
        String category,
        String description,
        Map<String, Object> attributes,
        BigDecimal price,
        boolean inStock) {

    public String document() {
        var attrs = new StringBuilder();
        attributes.forEach((key, value) -> attrs.append(key.replace('_', ' ')).append(": ").append(value).append(". "));
        return name + ". " + brand + ". " + category + ". " + description + " " + attrs;
    }
}
