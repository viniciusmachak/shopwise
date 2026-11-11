package com.shopwise.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CartIntegrationTests {

    @Autowired
    CartService carts;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    private long variantWithStock(int atLeast) {
        return jdbc.sql("select variant_id from inventory where quantity >= :q order by variant_id limit 1")
                .param("q", atLeast).query(Long.class).single();
    }

    private long soldOutVariant() {
        return jdbc.sql("select variant_id from inventory where quantity = 0 order by variant_id limit 1")
                .query(Long.class).single();
    }

    @Test
    void addingTheSameVariantTwiceIncreasesQuantity() {
        var key = CartKey.random();
        long variant = variantWithStock(3);

        carts.addItem(key, variant, 1);
        var cart = carts.addItem(key, variant, 2);

        assertThat(cart.items()).singleElement().satisfies(line -> {
            assertThat(line.quantity()).isEqualTo(3);
            assertThat(line.lineTotal().amount()).isEqualByComparingTo(line.unitPrice().amount().multiply(BigDecimal.valueOf(3)));
        });
        assertThat(cart.itemCount()).isEqualTo(3);
    }

    @Test
    void cannotAddMoreThanInStock() {
        var key = CartKey.random();

        assertThatThrownBy(() -> carts.addItem(key, soldOutVariant(), 1))
                .isInstanceOf(CartException.class)
                .extracting(e -> ((CartException) e).reason())
                .isEqualTo(CartException.Reason.OUT_OF_STOCK);
    }

    @Test
    void settingQuantityToZeroRemovesTheItem() {
        var key = CartKey.random();
        var cart = carts.addItem(key, variantWithStock(1), 1);

        cart = carts.updateItem(key, cart.items().getFirst().itemId(), 0);

        assertThat(cart.items()).isEmpty();
        assertThat(cart.total().amount()).isEqualByComparingTo("0");
    }

    @Test
    void couponDiscountsTheSubtotal() {
        var key = CartKey.random();
        carts.addItem(key, variantWithStock(2), 2);

        var cart = carts.applyCoupon(key, " welcome10 ");

        assertThat(cart.couponCode()).isEqualTo("WELCOME10");
        assertThat(cart.discount().amount()).isEqualByComparingTo(cart.subtotal().amount().multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(cart.total().amount()).isEqualByComparingTo(cart.subtotal().amount().subtract(cart.discount().amount()));
    }

    @Test
    void inactiveCouponIsRejected() {
        assertThatThrownBy(() -> carts.applyCoupon(CartKey.random(), "EXPIRED20"))
                .isInstanceOf(CartException.class)
                .hasMessage("That coupon code isn't valid.");
    }

    @Test
    void cartCookieIsIssuedAndReused() {
        long variant = variantWithStock(2);
        var first = mvc.post().uri("/api/cart/items").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\": %d, \"quantity\": 1}".formatted(variant))
                .exchange();
        assertThat(first).hasStatusOk().cookies().containsCookie("shopwise_cart");
        var cookie = first.getResponse().getCookie("shopwise_cart");

        assertThat(mvc.get().uri("/api/cart").cookie(cookie))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.itemCount")
                .isEqualTo(1);
    }

    @Test
    void outOfStockReturnsConflictProblem() {
        assertThat(mvc.post().uri("/api/cart/items").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\": %d, \"quantity\": 1}".formatted(soldOutVariant())))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.reason")
                .isEqualTo("OUT_OF_STOCK");
    }
}
