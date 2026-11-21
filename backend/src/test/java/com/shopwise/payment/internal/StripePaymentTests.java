package com.shopwise.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.RecordingMailSender;
import com.shopwise.TestcontainersConfiguration;
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderService;
import com.shopwise.order.OrderStatus;
import com.shopwise.order.OrderView;
import com.shopwise.order.PlaceOrderRequest;
import com.stripe.Stripe;

@SpringBootTest(properties = {
        "shopwise.payment.stripe.secret-key=sk_test_fake",
        "shopwise.payment.stripe.webhook-secret=whsec_test_secret"
})
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, StripePaymentTests.FakeStripe.class })
class StripePaymentTests {

    static final String WEBHOOK_SECRET = "whsec_test_secret";

    @TestConfiguration
    static class FakeStripe {

        @Bean
        @Primary
        FakeStripeCheckout fakeStripeCheckout() {
            return new FakeStripeCheckout();
        }
    }

    @Autowired
    FakeStripeCheckout stripe;

    @Autowired
    CheckoutService checkout;

    @Autowired
    OrderService orders;

    @Autowired
    CartService carts;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DefaultPaymentService payments;

    @Autowired
    RecordingMailSender mail;

    long customer;
    long address;
    CartKey cart;
    long variant;
    String email;

    @BeforeEach
    void setUp() {
        email = "stripe-" + UUID.randomUUID() + "@example.test";
        customer = jdbc.sql("insert into customer (email, name) values (:e, 'Pat') returning id")
                .param("e", email).query(Long.class).single();
        address = jdbc.sql("""
                insert into address (customer_id, recipient, line1, city, state, postal_code, is_default)
                values (:id, 'Pat', '1 Main St', 'Springfield', 'IL', '62701', true) returning id
                """).param("id", customer).query(Long.class).single();
        cart = carts.resolve(customer, null);
        variant = jdbc.sql("select variant_id from inventory where quantity >= 3 order by variant_id desc limit 1")
                .query(Long.class).single();
        carts.addItem(cart, variant, 1);
    }

    private OrderView place() {
        var quote = checkout.quote(customer, cart, null, null);
        return checkout.placeOrder(customer, cart,
                new PlaceOrderRequest(address, quote.shippingOption(), quote.cartHash(), UUID.randomUUID().toString()));
    }

    private int stock() {
        return jdbc.sql("select quantity from inventory where variant_id = :id").param("id", variant)
                .query(Integer.class).single();
    }

    private String sessionOf(long orderId) {
        return jdbc.sql("select reference from payment where order_id = :id").param("id", orderId)
                .query(String.class).single();
    }

    private static String event(String type, String sessionId, boolean paid) {
        return """
                {"id": "evt_%s", "object": "event", "api_version": "%s", "type": "%s",
                 "data": {"object": {"id": "%s", "object": "checkout.session",
                  "payment_status": "%s", "status": "%s", "payment_intent": %s}}}
                """.formatted(UUID.randomUUID(), Stripe.API_VERSION, type, sessionId, paid ? "paid" : "unpaid",
                paid ? "complete" : "expired", paid ? "\"pi_" + sessionId + "\"" : "null");
    }

    /** The Stripe-Signature header Stripe sends: t=timestamp,v1=HMAC-SHA256(secret, "t.payload"). */
    private static String sign(String payload, String secret) throws Exception {
        long t = Instant.now().getEpochSecond();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var digest = HexFormat.of().formatHex(mac.doFinal((t + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + t + ",v1=" + digest;
    }

    private int postWebhook(String payload, String signature) {
        return mvc.post().uri("/api/payments/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload)
                .exchange().getResponse().getStatus();
    }

    private OrderStatus statusOf(long orderId) {
        return orders.find(customer, orderId).orElseThrow().status();
    }

    @Test
    void anOrderWaitsForPaymentOnStripeWithStockReservedAndTheCartKept() {
        int before = stock();

        var order = place();

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.paymentUrl()).startsWith("https://checkout.stripe.test/pay/");
        assertThat(stock()).isEqualTo(before - 1);
        assertThat(carts.view(cart).items()).hasSize(1);
    }

    @Test
    void aSignedPaymentNotificationPlacesTheOrderOnce() throws Exception {
        var order = place();
        var session = sessionOf(order.id());
        var payload = event("checkout.session.completed", session, true);

        assertThat(postWebhook(payload, sign(payload, WEBHOOK_SECRET))).isEqualTo(200);
        assertThat(postWebhook(payload, sign(payload, WEBHOOK_SECRET))).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(order.id()) == OrderStatus.PAID);
        assertThat(carts.view(cart).items()).isEmpty();
        assertThat(orders.find(customer, order.id()).orElseThrow().paymentUrl()).isNull();
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> mail.sentTo(email).size() == 1);
    }

    @Test
    void unsignedOrForgedNotificationsAreRejected() throws Exception {
        var order = place();
        var payload = event("checkout.session.completed", sessionOf(order.id()), true);

        assertThat(postWebhook(payload, sign(payload, "whsec_wrong"))).isEqualTo(400);
        assertThat(mvc.post().uri("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(payload).exchange().getResponse().getStatus()).isEqualTo(400);
        assertThat(statusOf(order.id())).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void anExpiredCheckoutReleasesTheStock() throws Exception {
        int before = stock();
        var order = place();
        var payload = event("checkout.session.expired", sessionOf(order.id()), false);

        assertThat(postWebhook(payload, sign(payload, WEBHOOK_SECRET))).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(order.id()) == OrderStatus.CANCELLED);
        assertThat(stock()).isEqualTo(before);
    }

    @Test
    void missedNotificationsAreCaughtUpByAskingStripe() {
        var order = place();
        stripe.settle(sessionOf(order.id()), true);

        payments.reconcile(order.id());

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(order.id()) == OrderStatus.PAID);
    }

    @Test
    void cancellingAPaidOrderRefundsItOnStripe() throws Exception {
        var order = place();
        var session = sessionOf(order.id());
        var payload = event("checkout.session.completed", session, true);
        postWebhook(payload, sign(payload, WEBHOOK_SECRET));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(order.id()) == OrderStatus.PAID);

        orders.cancel(customer, order.id());

        assertThat(stripe.refunds).contains("pi_" + session);
    }
}
