package com.shopwise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.cart.CartService;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.PlaceOrderRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MetricsTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CartService carts;

    @Autowired
    CheckoutService checkout;

    @Test
    void prometheusCanScrapeBusinessMetricsWithoutSigningIn() {
        long customer = jdbc.sql("insert into customer (email, name) values (:e, 'M') returning id")
                .param("e", "metrics-" + UUID.randomUUID() + "@example.test").query(Long.class).single();
        long address = jdbc.sql("""
                insert into address (customer_id, recipient, line1, city, state, postal_code, is_default)
                values (:id, 'M', '1 Main St', 'Springfield', 'IL', '62701', true) returning id
                """).param("id", customer).query(Long.class).single();
        var cart = carts.resolve(customer, null);
        carts.addItem(cart, jdbc.sql("select variant_id from inventory where quantity >= 2 order by variant_id limit 1")
                .query(Long.class).single(), 1);
        var quote = checkout.quote(customer, cart, null, null);
        checkout.placeOrder(customer, cart,
                new PlaceOrderRequest(address, quote.shippingOption(), quote.cartHash(), UUID.randomUUID().toString()));

        assertThat(mvc.get().uri("/actuator/prometheus"))
                .hasStatusOk()
                .bodyText()
                .contains("shopwise_orders_placed_total")
                .contains("shopwise_orders_revenue_sum{application=\"shopwise\",currency=\"USD\"}")
                .contains("http_server_requests_seconds")
                .contains("application=\"shopwise\"");
    }

    @Test
    void otherActuatorEndpointsStayPrivate() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(401);
    }
}
