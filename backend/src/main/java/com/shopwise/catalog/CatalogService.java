package com.shopwise.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public API of the catalog module.
 */
public interface CatalogService {

    List<CategoryView> categories();

    Page<ProductSummary> findProducts(ProductFilter filter, Pageable pageable);

    Optional<ProductDetail> findProduct(long productId);

    /** Summaries in the same order as the given ids; unknown ids are skipped. */
    List<ProductSummary> findSummaries(List<Long> productIds);

    Optional<VariantView> findVariant(long variantId);

    List<VariantView> findVariants(Collection<Long> variantIds);

    List<Long> allProductIds();

    Optional<IndexableProduct> findIndexable(long productId);
}
