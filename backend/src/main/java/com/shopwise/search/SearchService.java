package com.shopwise.search;

import java.util.List;

import com.shopwise.catalog.ProductSummary;

/**
 * Public API of the search module.
 */
public interface SearchService {

    /**
     * Hybrid full-text and semantic search. Falls back to full-text only when
     * the embedding model is unavailable.
     */
    List<ProductSummary> search(SearchQuery query);

    /**
     * Products whose description is closest to this one's (by embedding), in the same category
     * when {@code sameCategory} is set. Empty when the product has no embedding yet.
     */
    List<ProductSummary> similar(long productId, boolean sameCategory, int limit);
}
