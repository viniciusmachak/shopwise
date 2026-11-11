package com.shopwise.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.TestcontainersConfiguration;
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.order.CheckoutException.Reason;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class CheckoutIntegrationTests {

    @Autowired
    CheckoutService checkout;

    @Autowired
    OrderService orders;

    @Autowired
    CartService carts;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ApplicationEvents events;

    @Autowired
    MockMvcTester mvc;

    OrderFixtures fixtures;
    long customer;
    long address;
    CartKey cart;

    @BeforeEach
    void setUp() {
        fixtures = new OrderFixtures(jdbc);
        customer = fixtures.customer();
        address = fixtures.address(customer, true);
        cart = carts.resolve(customer, null);
    }

    private OrderView place(CheckoutQuote quote, String key) {
        return checkout.placeOrder(customer, cart,
                new PlaceOrderRequest(address, quote.shippingOption(), quote.cartHash(), key));
    }

    private static Reason reasonOf(Throwable e) {
        return ((CheckoutException) e).reason();
    }

    @Test
    void quoteUsesTheDefaultAddressAndChargesStandardShippingBelowTheThreshold() {
        carts.addItem(cart, fixtures.variantPricedBelow("60", 2), 1);

        var quote = checkout.quote(customer, cart, null, null);

        assertThat(quote.address()).isNotNull();
        assertThat(quote.address().id()).isEqualTo(address);
        assertThat(quote.shipping().amount()).isEqualByComparingTo("5.00");
        assertThat(quote.total().amount()).isEqualByComparingTo(quote.subtotal().amount().add(new BigDecimal("5.00")));
    }

    @Test
    void standardShippingIsFreeFromOneHundredDollarsAndExpressIsFlat() {
        carts.addItem(cart, fixtures.variantPricedAtLeast("100", 2), 1);

        assertThat(checkout.quote(customer, cart, null, ShippingOption.STANDARD).shipping().amount())
                .isEqualByComparingTo("0");
        assertThat(checkout.quote(customer, cart, null, ShippingOption.EXPRESS).shipping().amount())
                .isEqualByComparingTo("15.00");
    }

    @Test
    void placingAnOrderTakesStockChargesAndEmptiesTheCart() {
        long variant = fixtures.variant(3, 0);
        int before = fixtures.stock(variant);
        carts.addItem(cart, variant, 2);

        var order = place(checkout.quote(customer, cart, null, null), UUID.randomUUID().toString());

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.items()).singleElement().extracting(OrderView.Line::quantity).isEqualTo(2);
        assertThat(order.cancellable()).isTrue();
        assertThat(fixtures.stock(variant)).isEqualTo(before - 2);
        assertThat(carts.view(cart).items()).isEmpty();
        assertThat(events.stream(OrderPlaced.class)).contains(new OrderPlaced(order.id(), customer));
        assertThat(jdbc.sql("select status from payment where order_id = :id").param("id", order.id())
                .query(String.class).single()).isEqualTo("APPROVED");
    }

    @Test
    void repeatingTheSameCheckoutReturnsTheSameOrder() {
        long variant = fixtures.variant(3, 1);
        int before = fixtures.stock(variant);
        carts.addItem(cart, variant, 1);
        var quote = checkout.quote(customer, cart, null, null);
        var key = UUID.randomUUID().toString();

        var first = place(quote, key);
        var second = place(quote, key);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(fixtures.stock(variant)).isEqualTo(before - 1);
    }

    @Test
    void aChangedCartNeedsANewQuote() {
        carts.addItem(cart, fixtures.variant(3, 2), 1);
        var quote = checkout.quote(customer, cart, null, null);
        carts.addItem(cart, fixtures.variant(3, 2), 1);

        assertThatThrownBy(() -> place(quote, UUID.randomUUID().toString()))
                .isInstanceOf(CheckoutException.class)
                .extracting(CheckoutIntegrationTests::reasonOf).isEqualTo(Reason.CART_CHANGED);
    }

    @Test
    void anEmptyCartCannotBeCheckedOut() {
        var quote = checkout.quote(customer, cart, null, null);

        assertThatThrownBy(() -> place(quote, UUID.randomUUID().toString()))
                .extracting(CheckoutIntegrationTests::reasonOf).isEqualTo(Reason.EMPTY_CART);
    }

    @Test
    void anotherCustomersAddressCannotBeUsed() {
        long stranger = fixtures.customer();
        long strangersAddress = fixtures.address(stranger, true);
        carts.addItem(cart, fixtures.variant(3, 3), 1);
        var quote = checkout.quote(customer, cart, null, null);

        assertThatThrownBy(() -> checkout.placeOrder(customer, cart,
                new PlaceOrderRequest(strangersAddress, ShippingOption.STANDARD, quote.cartHash(), "k-" + UUID.randomUUID())))
                .extracting(CheckoutIntegrationTests::reasonOf).isEqualTo(Reason.NO_ADDRESS);
    }

    @Test
    void itemsThatSellOutAfterTheQuoteStopTheOrderAndNothingIsSaved() {
        long variant = fixtures.variant(3, 4);
        carts.addItem(cart, variant, 1);
        var quote = checkout.quote(customer, cart, null, null);
        int stockBefore = fixtures.stock(variant);
        jdbc.sql("update inventory set quantity = 0 where variant_id = :id").param("id", variant).update();

        try {
            assertThatThrownBy(() -> place(quote, UUID.randomUUID().toString()))
                    .isInstanceOf(CheckoutException.class);
            assertThat(orders.list(customer)).isEmpty();
        }
        finally {
            jdbc.sql("update inventory set quantity = :q where variant_id = :id")
                    .param("q", stockBefore).param("id", variant).update();
        }
    }

    @Test
    void cancellingPutsStockBackAndRefunds() {
        long variant = fixtures.variant(3, 5);
        int before = fixtures.stock(variant);
        carts.addItem(cart, variant, 1);
        var order = place(checkout.quote(customer, cart, null, null), UUID.randomUUID().toString());

        var cancelled = orders.cancel(customer, order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.cancellable()).isFalse();
        assertThat(fixtures.stock(variant)).isEqualTo(before);
        assertThat(jdbc.sql("select status from payment where order_id = :id").param("id", order.id())
                .query(String.class).single()).isEqualTo("REFUNDED");
        assertThat(events.stream(OrderCancelled.class)).contains(new OrderCancelled(order.id(), customer));
        assertThatThrownBy(() -> orders.cancel(customer, order.id()))
                .extracting(CheckoutIntegrationTests::reasonOf).isEqualTo(Reason.NOT_CANCELLABLE);
    }

    @Test
    void customersOnlySeeTheirOwnOrders() {
        carts.addItem(cart, fixtures.variant(3, 6), 1);
        var order = place(checkout.quote(customer, cart, null, null), UUID.randomUUID().toString());
        long stranger = fixtures.customer();

        assertThat(orders.find(stranger, order.id())).isEmpty();
        assertThatThrownBy(() -> orders.cancel(stranger, order.id()))
                .extracting(CheckoutIntegrationTests::reasonOf).isEqualTo(Reason.NOT_FOUND);
        assertThat(orders.list(customer)).extracting(OrderSummary::id).containsExactly(order.id());
    }

    @Test
    void orderEndpointsNeedASignedInCustomer() {
        assertThat(mvc.get().uri("/api/orders")).hasStatus(401);
    }
}
