package com.shopwise.search.internal;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopwise.catalog.ProductSummary;
import com.shopwise.search.SearchQuery;
import com.shopwise.search.SearchService;

@RestController
@RequestMapping("/api/search")
class SearchController {

    private final SearchService search;

    SearchController(SearchService search) {
        this.search = search;
    }

    @GetMapping
    List<ProductSummary> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "12") int limit) {
        return search.search(new SearchQuery(q, category, minPrice, maxPrice, inStock, limit));
    }
}
