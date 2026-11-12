package com.shopwise.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Duration;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.RecordingMailSender;
import com.shopwise.TestcontainersConfiguration;
import com.shopwise.cart.CartService;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderService;
import com.shopwise.order.OrderStatus;
import com.shopwise.order.PlaceOrderRequest;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "shopwise.admin.emails=ops@example.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderAdminTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CartService carts;

    @Autowired
    CheckoutService checkout;

    @Autowired
    OrderService orders;

    @Autowired
    RecordingMailSender mail;

    Cookie admin;
    String customerEmail;
    long customer;
    long orderId;

    @BeforeEach
    void setUp() {
        jdbc.sql("delete from customer where email = 'ops@example.test'").update();
        admin = signUp("ops@example.test");

        customerEmail = "buyer-" + UUID.randomUUID() + "@example.test";
        customer = jdbc.sql("insert into customer (email, name) values (:e, 'Buyer') returning id")
                .param("e", customerEmail).query(Long.class).single();
        long address = jdbc.sql("""
                insert into address (customer_id, recipient, line1, city, state, postal_code, is_default)
                values (:id, 'Buyer', '5 Oak St', 'Springfield', 'IL', '62701', true) returning id
                """).param("id", customer).query(Long.class).single();
        var cart = carts.resolve(customer, null);
        long variant = jdbc.sql("select variant_id from inventory where quantity >= 3 order by variant_id limit 1")
                .query(Long.class).single();
        carts.addItem(cart, variant, 1);
        var quote = checkout.quote(customer, cart, null, null);
        orderId = checkout.placeOrder(customer, cart,
                new PlaceOrderRequest(address, quote.shippingOption(), quote.cartHash(), UUID.randomUUID().toString())).id();
    }

    private Cookie signUp(String email) {
        var result = mvc.post().uri("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"correct horse\", \"name\": \"Pat\"}".formatted(email))
                .exchange();
        return result.getResponse().getCookie("SESSION");
    }

    private void awaitMailWithSubject(String subject) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                mail.sentTo(customerEmail).stream().anyMatch(m -> subject.equals(m.getSubject())));
    }

    @Test
    void customersCannotSeeAllOrders() {
        var customerSession = signUp("nosy-" + UUID.randomUUID() + "@example.test");

        assertThat(mvc.get().uri("/api/admin/orders").cookie(customerSession)).hasStatus(403);
    }

    @Test
    void listsOrdersWithTheirCustomerAndFiltersByStatus() {
        assertThat(mvc.get().uri("/api/admin/orders?q=" + customerEmail).cookie(admin))
                .hasStatusOk().bodyJson().extractingPath("$[0].id").isEqualTo((int) orderId);
        assertThat(mvc.get().uri("/api/admin/orders?status=SHIPPED&q=" + customerEmail).cookie(admin))
                .hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(mvc.get().uri("/api/admin/orders/" + orderId).cookie(admin))
                .hasStatusOk().bodyJson().extractingPath("$.customerEmail").isEqualTo(customerEmail);
    }

    @Test
    void shippingAndDeliveringTellTheCustomer() {
        assertThat(mvc.post().uri("/api/admin/orders/" + orderId + "/ship").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"trackingNumber\": \"1Z999AA10123456784\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.order.status").isEqualTo("SHIPPED");
        awaitMailWithSubject("Your Shopwise order #" + orderId + " is on its way");
        assertThat(mail.sentTo(customerEmail).stream().filter(m -> m.getSubject().endsWith("is on its way"))
                .findFirst().orElseThrow().getText()).contains("1Z999AA10123456784");

        var view = orders.find(customer, orderId).orElseThrow();
        assertThat(view.trackingNumber()).isEqualTo("1Z999AA10123456784");
        assertThat(view.cancellable()).isFalse();

        assertThat(mvc.post().uri("/api/admin/orders/" + orderId + "/deliver").cookie(admin).with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$.order.status").isEqualTo("DELIVERED");
        awaitMailWithSubject("Your Shopwise order #" + orderId + " was delivered");
    }

    @Test
    void statusesOnlyMoveForward() {
        assertThat(mvc.post().uri("/api/admin/orders/" + orderId + "/deliver").cookie(admin).with(csrf()))
                .hasStatus(409);

        mvc.post().uri("/api/admin/orders/" + orderId + "/ship").cookie(admin).with(csrf()).exchange();

        assertThat(mvc.post().uri("/api/admin/orders/" + orderId + "/cancel").cookie(admin).with(csrf()))
                .hasStatus(409);
    }

    @Test
    void adminsCanCancelAPaidOrder() {
        assertThat(mvc.post().uri("/api/admin/orders/" + orderId + "/cancel").cookie(admin).with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$.order.status").isEqualTo("CANCELLED");
        assertThat(orders.find(customer, orderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
