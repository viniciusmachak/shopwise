package com.shopwise.recommendation.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.shopwise.catalog.CatalogService;
import com.shopwise.catalog.CategoryView;
import com.shopwise.catalog.ProductFilter;
import com.shopwise.catalog.ProductSummary;
import com.shopwise.order.OrderInsights;
import com.shopwise.recommendation.RecommendationService;
import com.shopwise.recommendation.Recommendations;
import com.shopwise.search.SearchService;

@Service
class DefaultRecommendationService implements RecommendationService {

    private final CatalogService catalog;
    private final SearchService search;
    private final OrderInsights orders;
    private final RecommendationProperties properties;

    DefaultRecommendationService(CatalogService catalog, SearchService search, OrderInsights orders,
            RecommendationProperties properties) {
        this.catalog = catalog;
        this.search = search;
        this.orders = orders;
        this.properties = properties;
    }

    @Override
    public Optional<Recommendations> forProduct(long productId, int limit) {
        var product = catalog.findSummaries(List.of(productId)).stream().findFirst();
        if (product.isEmpty()) {
            return Optional.empty();
        }
        int n = Math.clamp(limit, 1, 12);
        var similar = search.similar(productId, true, n).stream().filter(ProductSummary::inStock).limit(n).toList();
        return Optional.of(new Recommendations(similar, goesWith(product.get(), n)));
    }

    /** Often bought together first, then the best match in each complementary category. */
    private List<ProductSummary> goesWith(ProductSummary product, int limit) {
        var picks = new LinkedHashMap<Long, ProductSummary>();
        catalog.findSummaries(orders.boughtTogether(product.id(), limit)).stream()
                .filter(ProductSummary::inStock)
                .forEach(p -> picks.putIfAbsent(p.id(), p));

        var slug = categorySlug(product.category());
        var complementary = new ArrayList<>(properties.complements().getOrDefault(slug, List.of()));
        for (int round = 0; picks.size() < limit && round < 3; round++) {
            for (var category : complementary) {
                if (picks.size() >= limit) {
                    break;
                }
                var page = catalog.findProducts(new ProductFilter(category, null, null, true),
                        PageRequest.of(round, 1, Sort.by("name")));
                page.getContent().forEach(p -> picks.putIfAbsent(p.id(), p));
            }
        }
        return picks.values().stream().filter(p -> p.id() != product.id()).limit(limit).toList();
    }

    private String categorySlug(String categoryName) {
        return catalog.categories().stream()
                .filter(c -> c.name().equals(categoryName))
                .map(CategoryView::slug)
                .findFirst()
                .orElse("");
    }
}
