package com.shopwise.catalog;

import java.math.BigDecimal;

public record AdminProductRow(long id, String name, String brand, String category, BigDecimal price, boolean active,
        int variantCount, int totalStock) {
}
