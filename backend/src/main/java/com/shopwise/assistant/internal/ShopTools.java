package com.shopwise.assistant.internal;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.shopwise.cart.CartException;
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.cart.CartView;
import com.shopwise.catalog.CatalogService;
import com.shopwise.catalog.ProductSummary;
import com.shopwise.search.SearchQuery;
import com.shopwise.search.SearchService;

/**
 * Tools the model can call. One instance per request: the cart key comes from the
 * request's cookie, never from the model. Results are kept small on purpose.
 */
class ShopTools {

    private static final Logger log = LoggerFactory.getLogger(ShopTools.class);

    private static final int MAX_DESCRIPTION = 400;

    private final SearchService search;
    private final CatalogService catalog;
    private final CartService carts;
    private final AuditLog audit;
    private final CartKey cartKey;
    private final UUID conversationId;
    private final UiEvents ui;

    ShopTools(SearchService search, CatalogService catalog, CartService carts, AuditLog audit, CartKey cartKey,
            UUID conversationId, UiEvents ui) {
        this.search = search;
        this.catalog = catalog;
        this.carts = carts;
        this.audit = audit;
        this.cartKey = cartKey;
        this.conversationId = conversationId;
        this.ui = ui;
    }

    @Tool(description = "Search the store's products. Use it whenever the user looks for something to buy.")
    SearchResult searchProducts(
            @ToolParam(description = "What the user is looking for, in their words") String query,
            @ToolParam(required = false, description = "Category slug: running-shoes, hiking-boots, jackets, backpacks or t-shirts") String category,
            @ToolParam(required = false, description = "Maximum price in USD") BigDecimal maxPrice,
            @ToolParam(required = false, description = "Minimum price in USD") BigDecimal minPrice) {
        status("searchProducts");
        log.debug("searchProducts query={} category={} minPrice={} maxPrice={}", query, category, minPrice, maxPrice);
        var results = search.search(new SearchQuery(query, categorySlug(category), minPrice, maxPrice, null, 6));
        ui.emit("ui.products", results);
        return new SearchResult(results.stream().map(ProductBrief::of).toList());
    }

    @Tool(description = "Get one product's details, including the sizes available and their stock.")
    Object getProduct(@ToolParam(description = "Product id from a search result") long productId) {
        status("getProduct");
        return catalog.findProduct(productId)
                .<Object>map(p -> {
                    ui.emit("ui.products", catalog.findSummaries(List.of(p.id())));
                    return new ProductInfo(p.id(), p.name(), p.brand(), p.price().amount(),
                            "<product_data>" + truncate(p.description()) + "</product_data>",
                            p.attributes(),
                            p.variants().stream().map(v -> new SizeOption(v.size(), v.stock() > 0)).toList());
                })
                .orElse(new ToolError("No product with id " + productId + "."));
    }

    @Tool(description = "Show what is in the user's cart.")
    CartSummary viewCart() {
        status("viewCart");
        var cart = carts.view(cartKey);
        ui.emit("ui.cart", cart);
        return CartSummary.of(cart);
    }

    @Tool(description = "Add a product to the user's cart. The size is required when the product comes in several sizes.")
    Object addToCart(
            @ToolParam(description = "The id of the exact product the user chose, copied from a searchProducts result") long productId,
            @ToolParam(required = false, description = "Size, e.g. 10 for shoes or M for apparel") String size,
            @ToolParam(required = false, description = "How many, default 1") Integer quantity) {
        status("addToCart");
        int qty = quantity == null ? 1 : quantity;
        var args = new HashMap<String, Object>();
        args.put("productId", productId);
        args.put("size", size);
        args.put("quantity", qty);

        var product = catalog.findProduct(productId);
        if (product.isEmpty()) {
            return reject("addToCart", args, "No product with id " + productId + ".");
        }
        var variants = product.get().variants();
        var match = variants.size() == 1 && blankToNull(size) == null
                ? variants.stream().findFirst()
                : variants.stream().filter(v -> v.size() != null && v.size().equalsIgnoreCase(String.valueOf(size).trim())).findFirst();
        if (match.isEmpty()) {
            var sizes = variants.stream().map(v -> v.size()).toList();
            return reject("addToCart", args, "Please pick a size. Available sizes: " + sizes + ".");
        }
        try {
            var cart = carts.addItem(cartKey, match.get().id(), qty);
            audit.record(cartKey.value(), conversationId, "addToCart", args, true, null);
            ui.emit("ui.cart", cart);
            return CartSummary.of(cart);
        }
        catch (CartException e) {
            return reject("addToCart", args, e.getMessage());
        }
    }

    @Tool(description = "Change the quantity of an item already in the cart. Quantity 0 removes it.")
    Object updateCartItem(
            @ToolParam(description = "Item id from viewCart") long itemId,
            @ToolParam(description = "New quantity") int quantity) {
        status("updateCartItem");
        Map<String, Object> args = Map.of("itemId", itemId, "quantity", quantity);
        try {
            var cart = carts.updateItem(cartKey, itemId, quantity);
            audit.record(cartKey.value(), conversationId, "updateCartItem", args, true, null);
            ui.emit("ui.cart", cart);
            return CartSummary.of(cart);
        }
        catch (CartException e) {
            return reject("updateCartItem", args, e.getMessage());
        }
    }

    @Tool(description = "Apply a discount coupon code to the cart.")
    Object applyCoupon(@ToolParam(description = "The coupon code the user gave") String code) {
        status("applyCoupon");
        Map<String, Object> args = Map.of("code", code);
        try {
            var cart = carts.applyCoupon(cartKey, code);
            audit.record(cartKey.value(), conversationId, "applyCoupon", args, true, null);
            ui.emit("ui.cart", cart);
            return CartSummary.of(cart);
        }
        catch (CartException e) {
            return reject("applyCoupon", args, e.getMessage());
        }
    }

    private ToolError reject(String action, Map<String, Object> args, String message) {
        audit.record(cartKey.value(), conversationId, action, args, false, message);
        return new ToolError(message);
    }

    private void status(String tool) {
        ui.emit("tool_status", Map.of("tool", tool, "state", "running"));
    }

    /**
     * Models often send a category name ("Running Shoes") instead of the slug. Accept both,
     * and ignore categories that don't exist rather than returning nothing.
     */
    private String categorySlug(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        var wanted = category.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        return catalog.categories().stream()
                .filter(c -> c.slug().equals(wanted) || c.name().equalsIgnoreCase(category.trim()))
                .map(c -> c.slug())
                .findFirst()
                .orElse(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String truncate(String s) {
        return s.length() <= MAX_DESCRIPTION ? s : s.substring(0, MAX_DESCRIPTION) + "…";
    }

    record ProductBrief(long id, String name, String brand, BigDecimal price, boolean inStock) {

        static ProductBrief of(ProductSummary p) {
            return new ProductBrief(p.id(), p.name(), p.brand(), p.price().amount(), p.inStock());
        }
    }

    record SearchResult(List<ProductBrief> products) {
    }

    record SizeOption(String size, boolean inStock) {
    }

    record ProductInfo(long id, String name, String brand, BigDecimal price, String description,
            Map<String, Object> attributes, List<SizeOption> sizes) {
    }

    record CartLine(long itemId, String product, String size, int quantity, BigDecimal lineTotal) {
    }

    record CartSummary(List<CartLine> items, BigDecimal subtotal, String coupon, BigDecimal discount, BigDecimal total) {

        static CartSummary of(CartView cart) {
            return new CartSummary(
                    cart.items().stream()
                            .map(l -> new CartLine(l.itemId(), l.productName(), l.size(), l.quantity(), l.lineTotal().amount()))
                            .toList(),
                    cart.subtotal().amount(), cart.couponCode(), cart.discount().amount(), cart.total().amount());
        }
    }

    record ToolError(String error) {
    }
}
