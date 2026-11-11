package com.shopwise.backoffice.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shopwise.catalog.AdminProductRow;
import com.shopwise.catalog.AdminProductView;
import com.shopwise.catalog.CatalogAdmin;
import com.shopwise.catalog.CatalogAdminException;
import com.shopwise.catalog.ProductForm;
import com.shopwise.catalog.VariantForm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Admin-only (secured under /api/admin). Thin layer over the catalog's admin API.
 */
@RestController
@RequestMapping("/api/admin/products")
class ProductAdminController {

    private final CatalogAdmin catalog;

    ProductAdminController(CatalogAdmin catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<AdminProductRow> list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "true") boolean includeArchived) {
        return catalog.list(q, includeArchived);
    }

    @GetMapping("/{id}")
    AdminProductView get(@PathVariable long id) {
        return catalog.get(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminProductView create(@Valid @RequestBody ProductForm form) {
        return catalog.create(form);
    }

    @PutMapping("/{id}")
    AdminProductView update(@PathVariable long id, @Valid @RequestBody ProductForm form) {
        return catalog.update(id, form);
    }

    @PostMapping("/{id}/archive")
    AdminProductView archive(@PathVariable long id) {
        return catalog.setActive(id, false);
    }

    @PostMapping("/{id}/restore")
    AdminProductView restore(@PathVariable long id) {
        return catalog.setActive(id, true);
    }

    @PostMapping("/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    AdminProductView addVariant(@PathVariable long id, @Valid @RequestBody NewVariant body) {
        return catalog.addVariant(id, body.variant(), body.stock());
    }

    @PutMapping("/variants/{variantId}")
    AdminProductView updateVariant(@PathVariable long variantId, @Valid @RequestBody VariantForm form) {
        return catalog.updateVariant(variantId, form);
    }

    @PutMapping("/variants/{variantId}/stock")
    AdminProductView setStock(@PathVariable long variantId, @Valid @RequestBody Stock body) {
        return catalog.setStock(variantId, body.quantity());
    }

    @ExceptionHandler
    ProblemDetail handle(CatalogAdminException e) {
        var status = switch (e.reason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_SKU -> HttpStatus.CONFLICT;
            case UNKNOWN_CATEGORY, INVALID_STOCK -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }

    record NewVariant(@NotNull @Valid VariantForm variant, @Min(0) int stock) {
    }

    record Stock(@Min(0) int quantity) {
    }
}
