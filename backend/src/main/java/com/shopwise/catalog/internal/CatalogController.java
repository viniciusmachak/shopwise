package com.shopwise.catalog.internal;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shopwise.catalog.CatalogService;
import com.shopwise.catalog.CategoryView;
import com.shopwise.catalog.ProductDetail;
import com.shopwise.catalog.ProductFilter;
import com.shopwise.catalog.ProductSummary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    List<CategoryView> categories() {
        return catalog.categories();
    }

    @GetMapping("/products")
    Page<ProductSummary> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var filter = new ProductFilter(category, minPrice, maxPrice, inStock);
        return catalog.findProducts(filter, PageRequest.of(page, size, Sort.by("name")));
    }

    @GetMapping("/products/{id}")
    ProductDetail product(@PathVariable long id) {
        return catalog.findProduct(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
