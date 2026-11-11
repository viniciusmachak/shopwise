package com.shopwise.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shopwise.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CartOwnershipTests {

    @Autowired
    CartService carts;

    @Autowired
    JdbcClient jdbc;

    private static long newCustomerId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }

    private long variantWithStock(int atLeast, int skip) {
        return jdbc.sql("select variant_id from inventory where quantity >= :q order by variant_id offset :skip limit 1")
                .param("q", atLeast).param("skip", skip).query(Long.class).single();
    }

    @Test
    void anonymousVisitorsKeepTheirCookieCart() {
        var key = CartKey.random();

        assertThat(carts.resolve(null, key)).isEqualTo(key);
    }

    @Test
    void firstSignInAdoptsTheAnonymousCart() {
        var anonymous = CartKey.random();
        carts.addItem(anonymous, variantWithStock(1, 0), 1);
        long customer = newCustomerId();

        var resolved = carts.resolve(customer, anonymous);

        assertThat(resolved).isEqualTo(anonymous);
        assertThat(carts.resolve(customer, null)).isEqualTo(anonymous);
    }

    @Test
    void signingInMergesTheAnonymousCartIntoTheCustomersCart() {
        long customer = newCustomerId();
        var own = carts.resolve(customer, null);
        var picked = jdbc.sql("select variant_id from inventory where quantity >= 4 order by variant_id limit 2")
                .query(Long.class).list();
        long shared = picked.get(0);
        long onlyAnonymous = picked.get(1);
        carts.addItem(own, shared, 1);

        var anonymous = CartKey.random();
        carts.addItem(anonymous, shared, 2);
        carts.addItem(anonymous, onlyAnonymous, 1);
        carts.applyCoupon(anonymous, "WELCOME10");

        var resolved = carts.resolve(customer, anonymous);

        assertThat(resolved).isEqualTo(own);
        var cart = carts.view(own);
        assertThat(cart.items()).extracting(CartView.Line::variantId, CartView.Line::quantity)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(shared, 3),
                        org.assertj.core.groups.Tuple.tuple(onlyAnonymous, 1));
        assertThat(cart.couponCode()).isEqualTo("WELCOME10");
        assertThat(carts.view(anonymous).items()).isEmpty();
    }

    @Test
    void mergingNeverGoesAboveStock() {
        long customer = newCustomerId();
        var own = carts.resolve(customer, null);
        var variant = jdbc.sql("select variant_id from inventory where quantity between 2 and 9 order by variant_id limit 1")
                .query(Long.class).single();
        int stock = jdbc.sql("select quantity from inventory where variant_id = :id").param("id", variant)
                .query(Integer.class).single();
        carts.addItem(own, variant, stock);
        var anonymous = CartKey.random();
        carts.addItem(anonymous, variant, 1);

        carts.resolve(customer, anonymous);

        assertThat(carts.view(own).items()).singleElement().extracting(CartView.Line::quantity).isEqualTo(stock);
    }

    @Test
    void anonymousVisitorsNeverGetACustomersCart() {
        long customer = newCustomerId();
        var own = carts.resolve(customer, null);

        var afterSignOut = carts.resolve(null, own);

        assertThat(afterSignOut).isNotEqualTo(own);
    }

    @Test
    void clearEmptiesTheCart() {
        var key = CartKey.random();
        carts.addItem(key, variantWithStock(1, 0), 1);
        carts.applyCoupon(key, "WELCOME10");

        carts.clear(key);

        assertThat(carts.view(key).items()).isEmpty();
        assertThat(carts.view(key).couponCode()).isNull();
    }
}
