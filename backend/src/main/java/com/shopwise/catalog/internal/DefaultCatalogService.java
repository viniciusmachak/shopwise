package com.shopwise.catalog.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.catalog.CatalogService;
import com.shopwise.catalog.CategoryView;
import com.shopwise.catalog.IndexableProduct;
import com.shopwise.catalog.Money;
import com.shopwise.catalog.ProductDetail;
import com.shopwise.catalog.ProductFilter;
import com.shopwise.catalog.ProductSummary;
import com.shopwise.catalog.VariantView;

@Service
@Transactional(readOnly = true)
class DefaultCatalogService implements CatalogService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;

    DefaultCatalogService(CategoryRepository categories, ProductRepository products,
            ProductVariantRepository variants) {
        this.categories = categories;
        this.products = products;
        this.variants = variants;
    }

    @Override
    public List<CategoryView> categories() {
        return categories.findAllByOrderByName().stream()
                .map(c -> new CategoryView(c.getSlug(), c.getName()))
                .toList();
    }

    @Override
    public Page<ProductSummary> findProducts(ProductFilter filter, Pageable pageable) {
        var page = products.findAll(ProductSpecifications.matching(filter), pageable);
        var inStock = new HashSet<>(variants.findProductIdsInStock(page.map(Product::getId).getContent()));
        return page.map(p -> toSummary(p, inStock.contains(p.getId())));
    }

    @Override
    public Optional<ProductDetail> findProduct(long productId) {
        return products.findById(productId).filter(Product::isActive).map(p -> new ProductDetail(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getBrand(),
                p.getCategory().getName(),
                p.getDescription(),
                new Money(p.getPrice(), p.getCurrency()),
                p.getAttributes(),
                p.getImageUrl(),
                variants.findByProductIdOrderById(p.getId()).stream().map(this::toView).toList()));
    }

    @Override
    public List<ProductSummary> findSummaries(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = products.findByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        var inStock = new HashSet<>(variants.findProductIdsInStock(productIds));
        return productIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(Product::isActive)
                .map(p -> toSummary(p, inStock.contains(p.getId())))
                .toList();
    }

    @Override
    public Optional<VariantView> findVariant(long variantId) {
        return findVariants(List.of(variantId)).stream().findFirst();
    }

    @Override
    public List<VariantView> findVariants(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return variants.findByIdIn(variantIds).stream().map(this::toView).toList();
    }

    @Override
    public List<Long> allProductIds() {
        return products.findAllIds();
    }

    @Override
    public Optional<IndexableProduct> findIndexable(long productId) {
        return products.findById(productId).filter(Product::isActive).map(p -> new IndexableProduct(
                p.getId(),
                p.getName(),
                p.getBrand(),
                p.getCategory().getSlug(),
                p.getDescription(),
                p.getAttributes(),
                p.getPrice(),
                !variants.findProductIdsInStock(List.of(p.getId())).isEmpty()));
    }

    private ProductSummary toSummary(Product p, boolean inStock) {
        return new ProductSummary(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getBrand(),
                p.getCategory().getName(),
                new Money(p.getPrice(), p.getCurrency()),
                p.getImageUrl(),
                inStock);
    }

    private VariantView toView(ProductVariant v) {
        var product = v.getProduct();
        return new VariantView(
                v.getId(),
                product.getId(),
                product.getName(),
                v.getSku(),
                v.getSize(),
                v.getColor(),
                new Money(v.effectivePrice(), product.getCurrency()),
                // Archived products can't be bought: they show as sold out in carts.
                product.isActive() ? v.stock() : 0);
    }
}
