package com.shopwise.assistant.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import com.shopwise.recommendation.RecommendationService;
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
    private final RecommendationService recommendations;

    ShopTools(SearchService search, CatalogService catalog, CartService carts, AuditLog audit, CartKey cartKey,
            UUID conversationId, UiEvents ui, RecommendationService recommendations) {
        this.recommendations = recommendations;
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

    @Tool(description = """
            Compare two to four products side by side: price, brand, attributes and sizes in stock.
            Use it when the user asks to compare products or which one is better for something.""")
    Object compareProducts(
            @ToolParam(description = "Names of the products to compare, as shown in search results") List<String> productNames) {
        status("compareProducts");
        if (productNames == null || productNames.size() < 2) {
            return new ToolError("Give at least two products to compare.");
        }
        var compared = new ArrayList<Comparison.Item>();
        for (var name : productNames.stream().distinct().limit(4).toList()) {
            var resolved = resolveProduct(name);
            if (resolved instanceof ToolError error) {
                return error;
            }
            catalog.findProduct((Long) resolved).ifPresent(p -> compared.add(new Comparison.Item(p.id(), p.name(),
                    p.brand(), p.category(), p.price().amount(), p.attributes(),
                    p.variants().stream().filter(v -> v.stock() > 0).map(v -> v.size() == null ? "one size" : v.size()).toList())));
        }
        var attributes = compared.stream().flatMap(i -> i.attributes().keySet().stream()).distinct().sorted().toList();
        var comparison = new Comparison(compared, attributes);
        ui.emit("ui.compare", comparison);
        return comparison;
    }

    @Tool(description = """
            Recommend products related to one product. kind "similar" finds alternatives to it; kind "goes_with"
            finds products to buy with it (often bought together, or from complementary categories).""")
    Object recommendProducts(
            @ToolParam(description = "Product name, as shown in search results") String productName,
            @ToolParam(required = false, description = "similar or goes_with, default similar") String kind) {
        status("recommendProducts");
        var resolved = resolveProduct(productName);
        if (resolved instanceof ToolError error) {
            return error;
        }
        var found = recommendations.forProduct((Long) resolved, 4);
        if (found.isEmpty()) {
            return new ToolError("No recommendations for " + productName + ".");
        }
        var products = "goes_with".equalsIgnoreCase(kind) ? found.get().goesWith() : found.get().similar();
        if (products.isEmpty()) {
            return new ToolError("No recommendations for " + productName + " yet.");
        }
        ui.emit("ui.products", products);
        return new SearchResult(products.stream().map(ProductBrief::of).toList());
    }

    @Tool(description = "Show what is in the user's cart.")
    CartSummary viewCart() {
        status("viewCart");
        var cart = carts.view(cartKey);
        ui.emit("ui.cart", cart);
        return CartSummary.of(cart);
    }

    @Tool(description = """
            Add a product to the user's cart. Give the product's name as shown in search results.
            The size is required when the product comes in several sizes.""")
    Object addToCart(
            @ToolParam(description = "Product name, e.g. Trail Runner X") String productName,
            @ToolParam(required = false, description = "Size, e.g. 10 for shoes or M for apparel") String size,
            @ToolParam(required = false, description = "How many, default 1") Integer quantity) {
        status("addToCart");
        int qty = quantity == null ? 1 : quantity;
        var args = new HashMap<String, Object>();
        args.put("productName", productName);
        args.put("size", size);
        args.put("quantity", qty);

        var resolved = resolveProduct(productName);
        if (resolved instanceof ToolError error) {
            return reject("addToCart", args, error.error());
        }
        var product = catalog.findProduct((Long) resolved).orElseThrow();
        args.put("productId", product.id());
        var variants = product.variants();
        var match = variants.size() == 1 && blankToNull(size) == null
                ? variants.stream().findFirst()
                : variants.stream().filter(v -> v.size() != null && v.size().equalsIgnoreCase(String.valueOf(size).trim())).findFirst();
        if (match.isEmpty()) {
            var sizes = variants.stream().map(v -> v.size()).toList();
            return reject("addToCart", args, "Please pick a size for " + product.name() + ". Available sizes: " + sizes + ".");
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

    @Tool(description = "Change how many of a product are in the cart. Quantity 0 removes it.")
    Object updateCartItem(
            @ToolParam(description = "Product name as it appears in the cart") String productName,
            @ToolParam(required = false, description = "Size, when the cart has the product in several sizes") String size,
            @ToolParam(description = "New quantity") int quantity) {
        status("updateCartItem");
        var args = new HashMap<String, Object>();
        args.put("productName", productName);
        args.put("size", size);
        args.put("quantity", quantity);
        var wanted = normalize(productName);
        var lines = carts.view(cartKey).items().stream()
                .filter(l -> normalize(l.productName()).contains(wanted) || wanted.contains(normalize(l.productName())))
                .filter(l -> blankToNull(size) == null || (l.size() != null && l.size().equalsIgnoreCase(size.trim())))
                .toList();
        if (lines.isEmpty()) {
            return reject("updateCartItem", args, productName + " isn't in the cart.");
        }
        if (lines.size() > 1) {
            return reject("updateCartItem", args, "The cart has " + productName + " in several sizes: "
                    + lines.stream().map(l -> l.size()).toList() + ". Which one?");
        }
        try {
            var cart = carts.updateItem(cartKey, lines.getFirst().itemId(), quantity);
            audit.record(cartKey.value(), conversationId, "updateCartItem", args, true, null);
            ui.emit("ui.cart", cart);
            return CartSummary.of(cart);
        }
        catch (CartException e) {
            return reject("updateCartItem", args, e.getMessage());
        }
    }

    /**
     * Finds a product by the name the model gives. Tool results don't survive between turns,
     * so the model often no longer has product ids, but it does have the names it showed.
     * Returns the product id, or a ToolError asking the user to be more specific.
     */
    private Object resolveProduct(String name) {
        if (name == null || name.isBlank()) {
            return new ToolError("Which product? Please give its name.");
        }
        var wanted = normalize(name);
        var candidates = search.search(SearchQuery.of(name));
        var exact = candidates.stream().filter(p -> normalize(p.name()).equals(wanted)).findFirst();
        if (exact.isPresent()) {
            return exact.get().id();
        }
        var partial = candidates.stream()
                .filter(p -> normalize(p.name()).contains(wanted) || wanted.contains(normalize(p.name())))
                .toList();
        if (partial.size() == 1) {
            return partial.getFirst().id();
        }
        var names = candidates.stream().limit(3).map(ProductSummary::name).toList();
        return new ToolError(names.isEmpty()
                ? "No product called " + name + "."
                : "Which product do you mean: " + String.join(", ", names) + "?");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
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

    record Comparison(List<Item> products, List<String> attributes) {

        record Item(long id, String name, String brand, String category, BigDecimal price,
                Map<String, Object> attributes, List<String> sizesInStock) {
        }
    }

    record SizeOption(String size, boolean inStock) {
    }

    record ProductInfo(long id, String name, String brand, BigDecimal price, String description,
            Map<String, Object> attributes, List<SizeOption> sizes) {
    }

    record CartLine(String product, String size, int quantity, BigDecimal lineTotal) {
    }

    record CartSummary(List<CartLine> items, BigDecimal subtotal, String coupon, BigDecimal discount, BigDecimal total) {

        static CartSummary of(CartView cart) {
            return new CartSummary(
                    cart.items().stream()
                            .map(l -> new CartLine(l.productName(), l.size(), l.quantity(), l.lineTotal().amount()))
                            .toList(),
                    cart.subtotal().amount(), cart.couponCode(), cart.discount().amount(), cart.total().amount());
        }
    }

    record ToolError(String error) {
    }
}
