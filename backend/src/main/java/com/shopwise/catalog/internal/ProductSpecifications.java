package com.shopwise.catalog.internal;

import java.util.ArrayList;

import org.springframework.data.jpa.domain.Specification;

import com.shopwise.catalog.ProductFilter;

import jakarta.persistence.criteria.Predicate;

final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<Product> matching(ProductFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isTrue(root.get("active")));
            if (filter.category() != null && !filter.category().isBlank()) {
                predicates.add(cb.equal(root.get("category").get("slug"), filter.category()));
            }
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
            }
            if (Boolean.TRUE.equals(filter.inStock())) {
                var sub = query.subquery(Long.class);
                var variant = sub.from(ProductVariant.class);
                sub.select(variant.get("id"))
                        .where(cb.equal(variant.get("product"), root),
                                cb.greaterThan(variant.get("inventory").get("quantity"), 0));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
