package com.shopwise.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shopwise.TestcontainersConfiguration;
import com.shopwise.assistant.internal.ShopTools.CartSummary;
import com.shopwise.assistant.internal.ShopTools.ToolError;
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.catalog.CatalogService;
import com.shopwise.search.SearchService;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShopToolsIntegrationTests {

    @Autowired
    SearchService search;

    @Autowired
    CatalogService catalog;

    @Autowired
    CartService carts;

    @Autowired
    AuditLog audit;

    @Autowired
    JdbcClient jdbc;

    private final CartKey cartKey = CartKey.random();
    private final UUID conversationId = UUID.randomUUID();
    private final UiEvents ui = new UiEvents();

    private ShopTools tools() {
        return new ShopTools(search, catalog, carts, audit, cartKey, conversationId, ui);
    }

    private long productId(String slug) {
        return jdbc.sql("select id from product where slug = :slug").param("slug", slug).query(Long.class).single();
    }

    private String sizeInStock(long productId) {
        return jdbc.sql("""
                select v.size from product_variant v join inventory i on i.variant_id = v.id
                where v.product_id = :id and i.quantity > 0 order by v.id limit 1
                """).param("id", productId).query(String.class).single();
    }

    @Test
    void addsTheRequestedSizeToTheRequestCart() {
        long id = productId("trail-runner-x");
        var size = sizeInStock(id);

        var result = tools().addToCart("Trail Runner X", size, 1);

        assertThat(result).isInstanceOf(CartSummary.class);
        assertThat(carts.view(cartKey).items()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(id);
            assertThat(line.size()).isEqualTo(size);
        });
    }

    @Test
    void asksForASizeWhenItIsMissing() {
        var result = tools().addToCart("Trail Runner X", null, 1);

        assertThat(result).isInstanceOfSatisfying(ToolError.class,
                e -> assertThat(e.error()).startsWith("Please pick a size"));
        assertThat(carts.view(cartKey).items()).isEmpty();
    }

    @Test
    void singleSizeProductsDoNotNeedASize() {
        long backpackInStock = jdbc.sql("""
                select p.id from product p
                join category c on c.id = p.category_id and c.slug = 'backpacks'
                join product_variant v on v.product_id = p.id
                join inventory i on i.variant_id = v.id and i.quantity > 0
                order by p.id limit 1
                """).query(Long.class).single();

        var name = jdbc.sql("select name from product where id = :id").param("id", backpackInStock)
                .query(String.class).single();
        assertThat(tools().addToCart(name, null, 1)).isInstanceOf(CartSummary.class);
    }

    @Test
    void cartChangesAreAudited() {
        long id = productId("trail-runner-x");
        tools().addToCart("Trail Runner X", sizeInStock(id), 1);
        tools().applyCoupon("NOPE");

        var outcomes = jdbc.sql("select action || ':' || outcome from audit_log where conversation_id = :id order by id")
                .param("id", conversationId).query(String.class).list();

        assertThat(outcomes).containsExactly("addToCart:OK", "applyCoupon:REJECTED");
    }

    @Test
    void productDescriptionIsWrappedAsData() {
        var result = tools().getProduct(productId("rainshell-25"));

        assertThat(result).isInstanceOfSatisfying(ShopTools.ProductInfo.class,
                p -> assertThat(p.description()).startsWith("<product_data>").endsWith("</product_data>"));
    }

    @Test
    void searchEmitsProductCards() {
        tools().searchProducts("rain jacket", null, null, null);
        ui.complete();

        var events = ui.flux().map(e -> e.event()).collectList().block();

        assertThat(events).containsExactly("tool_status", "ui.products");
    }

    @Test
    void acceptsCategoryNamesAndIgnoresUnknownCategories() {
        var byName = tools().searchProducts("waterproof", "Running Shoes", null, null);
        var unknown = tools().searchProducts("Stormguard GTX Runner", "sneakers", null, null);

        assertThat(byName.products()).extracting(ShopTools.ProductBrief::name).contains("Stormguard GTX Runner");
        assertThat(unknown.products()).extracting(ShopTools.ProductBrief::name).contains("Stormguard GTX Runner");
    }

    @Test
    void findsProductsByNameAndAsksWhenTheNameIsAmbiguous() {
        long id = productId("trail-runner-x");

        assertThat(tools().addToCart("trail runner x", sizeInStock(id), 1)).isInstanceOf(CartSummary.class);
        assertThat(tools().addToCart("Runner", "9", 1)).isInstanceOfSatisfying(ToolError.class,
                e -> assertThat(e.error()).startsWith("Which product do you mean"));
    }

    @Test
    void changesQuantitiesByProductName() {
        long id = productId("trail-runner-x");
        tools().addToCart("Trail Runner X", sizeInStock(id), 1);

        var result = tools().updateCartItem("Trail Runner X", null, 0);

        assertThat(result).isInstanceOf(CartSummary.class);
        assertThat(carts.view(cartKey).items()).isEmpty();
    }
}
