package com.shopwise.recommendation;

import java.util.List;

import com.shopwise.catalog.ProductSummary;

/**
 * @param similar  alternatives to the product: same category, closest descriptions
 * @param goesWith products to buy with it: often bought together, else complementary categories
 */
public record Recommendations(List<ProductSummary> similar, List<ProductSummary> goesWith) {
}
