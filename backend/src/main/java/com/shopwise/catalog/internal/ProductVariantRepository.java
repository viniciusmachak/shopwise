package com.shopwise.catalog.internal;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @EntityGraph(attributePaths = {"product", "inventory"})
    List<ProductVariant> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "inventory")
    List<ProductVariant> findByProductIdOrderById(Long productId);

    @Query("""
            select v.product.id from ProductVariant v
            where v.product.id in :productIds and v.inventory.quantity > 0
            group by v.product.id
            """)
    List<Long> findProductIdsInStock(Collection<Long> productIds);
}
