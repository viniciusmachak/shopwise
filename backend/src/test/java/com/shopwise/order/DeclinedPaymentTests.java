package com.shopwise.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shopwise.TestcontainersConfiguration;
import com.shopwise.cart.CartService;

@SpringBootTest(properties = "shopwise.payment.simulated.decline-all=true")
@Import(TestcontainersConfiguration.class)
class DeclinedPaymentTests {

    @Autowired
    CheckoutService checkout;

    @Autowired
    OrderService orders;

    @Autowired
    CartService carts;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aDeclinedPaymentLeavesNoOrderAndKeepsStockAndCart() {
        var fixtures = new OrderFixtures(jdbc);
        long customer = fixtures.customer();
        long address = fixtures.address(customer, true);
        var cart = carts.resolve(customer, null);
        long variant = fixtures.variant(2, 0);
        int before = fixtures.stock(variant);
        carts.addItem(cart, variant, 1);
        var quote = checkout.quote(customer, cart, null, null);

        assertThatThrownBy(() -> checkout.placeOrder(customer, cart,
                new PlaceOrderRequest(address, quote.shippingOption(), quote.cartHash(), UUID.randomUUID().toString())))
                .isInstanceOf(CheckoutException.class)
                .extracting(e -> ((CheckoutException) e).reason())
                .isEqualTo(CheckoutException.Reason.PAYMENT_DECLINED);

        assertThat(orders.list(customer)).isEmpty();
        assertThat(fixtures.stock(variant)).isEqualTo(before);
        assertThat(carts.view(cart).items()).hasSize(1);
    }
}
