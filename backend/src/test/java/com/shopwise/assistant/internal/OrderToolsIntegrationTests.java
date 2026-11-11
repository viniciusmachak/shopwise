package com.shopwise.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.TestcontainersConfiguration;
import com.shopwise.assistant.internal.OrderTools.CancelSummary;
import com.shopwise.assistant.internal.OrderTools.CheckoutSummary;
import com.shopwise.assistant.internal.OrderTools.OrderInfo;
import com.shopwise.assistant.internal.ShopTools.ToolError;
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderService;
import com.shopwise.order.OrderStatus;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderToolsIntegrationTests {

    @Autowired
    CheckoutService checkout;

    @Autowired
    OrderService orders;

    @Autowired
    PendingActions pending;

    @Autowired
    AuditLog audit;

    @Autowired
    CartService carts;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    long customer;
    CartKey cart;
    UiEvents ui;
    UUID conversation;

    @BeforeEach
    void setUp() {
        customer = newCustomer(true);
        cart = carts.resolve(customer, null);
        ui = new UiEvents();
        conversation = UUID.randomUUID();
    }

    private long newCustomer(boolean withAddress) {
        long id = jdbc.sql("insert into customer (email, name) values (:e, 'Pat') returning id")
                .param("e", "tools-" + UUID.randomUUID() + "@example.com").query(Long.class).single();
        if (withAddress) {
            jdbc.sql("""
                    insert into address (customer_id, recipient, line1, city, state, postal_code, is_default)
                    values (:id, 'Pat Doe', '123 Main St', 'Springfield', 'IL', '62701', true)
                    """).param("id", id).update();
        }
        return id;
    }

    private OrderTools tools(@Nullable Long customerId, CartKey cartKey) {
        return new OrderTools(customerId, cartKey, conversation, ui, checkout, orders, pending, audit);
    }

    private void addSomething(CartKey key) {
        long variant = jdbc.sql("select variant_id from inventory where quantity >= 3 order by variant_id desc limit 1")
                .query(Long.class).single();
        carts.addItem(key, variant, 1);
    }

    private String latestToken() {
        return jdbc.sql("select token from pending_action where customer_id = :c order by created_at desc limit 1")
                .param("c", customer).query(String.class).single();
    }

    private List<String> uiEvents() {
        ui.complete();
        return ui.flux().map(e -> e.event()).filter(e -> !"tool_status".equals(e)).collectList().block();
    }

    @Test
    void visitorsAreAskedToSignIn() {
        var result = tools(null, CartKey.random()).prepareCheckout(null);

        assertThat(result).isInstanceOf(ToolError.class);
        assertThat(uiEvents()).containsExactly("ui.signin");
    }

    @Test
    void checkoutNeedsASavedAddress() {
        long noAddress = newCustomer(false);
        var key = carts.resolve(noAddress, null);
        addSomething(key);

        assertThat(tools(noAddress, key).prepareCheckout(null)).isInstanceOfSatisfying(ToolError.class,
                e -> assertThat(e.error()).contains("shipping address"));
    }

    @Test
    void prepareShowsASummaryWithoutPlacingAnythingAndConfirmPlacesTheOrderOnce() {
        addSomething(cart);
        var tools = tools(customer, cart);

        var summary = (CheckoutSummary) tools.prepareCheckout("express");

        assertThat(summary.shipTo()).isEqualTo("Main St, Springfield").doesNotContain("123");
        assertThat(summary.shippingSpeed()).isEqualTo("express");
        assertThat(orders.list(customer)).isEmpty();

        var placed = (OrderInfo) tools.confirmCheckout();
        var again = tools.confirmCheckout();

        assertThat(placed.status()).isEqualTo(OrderStatus.PAID);
        assertThat(again).isInstanceOf(ToolError.class);
        assertThat(orders.list(customer)).hasSize(1);
        assertThat(carts.view(cart).items()).isEmpty();
        assertThat(uiEvents()).containsExactly("ui.confirm", "ui.order");
    }

    @Test
    void confirmingWithoutASummaryInThisConversationDoesNothing() {
        addSomething(cart);

        assertThat(tools(customer, cart).confirmCheckout()).isInstanceOf(ToolError.class);

        tools(customer, cart).prepareCheckout(null);
        conversation = UUID.randomUUID();
        assertThat(tools(customer, cart).confirmCheckout()).isInstanceOf(ToolError.class);
        assertThat(orders.list(customer)).isEmpty();
    }

    @Test
    void anotherCustomerCannotUseTheToken() {
        addSomething(cart);
        tools(customer, cart).prepareCheckout(null);
        long stranger = newCustomer(true);

        assertThatThrownBy(() -> pending.confirm(latestToken(), stranger)).isInstanceOf(PendingActionException.class);
        assertThat(tools(stranger, carts.resolve(stranger, null)).confirmCheckout()).isInstanceOf(ToolError.class);
        assertThat(orders.list(customer)).isEmpty();
    }

    @Test
    void expiredAndDiscardedTokensAreRejected() {
        addSomething(cart);
        tools(customer, cart).prepareCheckout(null);
        var expired = latestToken();
        jdbc.sql("update pending_action set expires_at = now() - interval '1 minute' where token = :t")
                .param("t", expired).update();

        assertThat(tools(customer, cart).confirmCheckout()).isInstanceOf(ToolError.class);
        assertThatThrownBy(() -> pending.confirm(expired, customer)).hasMessageContaining("expired");

        tools(customer, cart).prepareCheckout(null);
        pending.discard(latestToken(), customer);

        assertThat(tools(customer, cart).confirmCheckout()).isInstanceOf(ToolError.class);
        assertThat(orders.list(customer)).isEmpty();
    }

    @Test
    void aFailedConfirmationLeavesTheTokenUsable() {
        addSomething(cart);
        tools(customer, cart).prepareCheckout(null);
        addSomething(cart);

        assertThat(tools(customer, cart).confirmCheckout()).isInstanceOfSatisfying(ToolError.class,
                e -> assertThat(e.error()).contains("changed"));
        assertThat(jdbc.sql("select used_at is null from pending_action where token = :t")
                .param("t", latestToken()).query(Boolean.class).single()).isTrue();
    }

    @Test
    void cancellingAlsoNeedsConfirmation() {
        addSomething(cart);
        var tools = tools(customer, cart);
        tools.prepareCheckout(null);
        var placed = (OrderInfo) tools.confirmCheckout();

        assertThat(tools.prepareCancel(placed.orderId())).isInstanceOf(CancelSummary.class);
        assertThat(orders.find(customer, placed.orderId()).orElseThrow().status()).isEqualTo(OrderStatus.PAID);

        var cancelled = (OrderInfo) tools.confirmCancel();

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(tools.prepareCancel(placed.orderId())).isInstanceOfSatisfying(ToolError.class,
                e -> assertThat(e.error()).contains("cancelled"));
    }

    @Test
    void ordersAreOnlyVisibleToTheirOwner() {
        addSomething(cart);
        var tools = tools(customer, cart);
        tools.prepareCheckout(null);
        var placed = (OrderInfo) tools.confirmCheckout();
        long stranger = newCustomer(true);

        assertThat(tools(stranger, carts.resolve(stranger, null)).trackOrder(placed.orderId()))
                .isInstanceOf(ToolError.class);
        assertThat(tools(stranger, carts.resolve(stranger, null)).prepareCancel(placed.orderId()))
                .isInstanceOf(ToolError.class);
    }

    @Test
    void theConfirmButtonNeedsASignedInCustomer() {
        assertThat(mvc.post().uri("/api/assistant/pending-actions/whatever/confirm").with(csrf())).hasStatus(401);
        assertThatThrownBy(() -> pending.confirm("not-a-token", customer)).isInstanceOf(PendingActionException.class);
    }
}
