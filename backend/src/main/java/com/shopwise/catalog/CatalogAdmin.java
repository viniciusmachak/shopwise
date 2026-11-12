package com.shopwise.catalog;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Public API for managing the catalog from the backoffice. Every change publishes
 * {@link ProductUpdated} so search and other read models follow.
 */
public interface CatalogAdmin {

    /** Products matching the text (name, brand or SKU), including archived ones when asked. */
    List<AdminProductRow> list(@Nullable String query, boolean includeArchived);

    Optional<AdminProductView> get(long productId);

    AdminProductView create(ProductForm form);

    AdminProductView update(long productId, ProductForm form);

    /** Archived products are hidden from the store, search and the assistant, and can't be bought. */
    AdminProductView setActive(long productId, boolean active);

    AdminProductView addVariant(long productId, VariantForm form, int initialStock);

    AdminProductView updateVariant(long variantId, VariantForm form);

    AdminProductView setStock(long variantId, int quantity);

    enum ImportOutcome { CREATED_PRODUCT, ADDED_VARIANT, UPDATED_VARIANT }

    /**
     * Upserts one import row: finds the product by name and brand (creating it if new, updating
     * its price, category and description otherwise), then the variant by SKU, and sets its stock.
     */
    ImportOutcome importLine(CatalogImportLine line);
}
