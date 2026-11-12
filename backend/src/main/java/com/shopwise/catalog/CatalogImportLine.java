package com.shopwise.catalog;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * One validated row of a catalog import: a variant and the product it belongs to.
 */
public record CatalogImportLine(
        String sku,
        String productName,
        String brand,
        String category,
        @Nullable String description,
        BigDecimal price,
        @Nullable String size,
        @Nullable String color,
        int stock) {
}
