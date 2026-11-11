package com.shopwise.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopwise.RecordingMailSender;
import com.shopwise.TestcontainersConfiguration;
import com.shopwise.cart.CartService;
import com.shopwise.customer.PasswordResetRequested;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderService;
import com.shopwise.order.PlaceOrderRequest;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EmailNotificationTests {

    @Autowired
    RecordingMailSender mail;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CartService carts;

    @Autowired
    CheckoutService checkout;

    @Autowired
    OrderService orders;

    private String email;

    private long customer() {
        email = "mail-" + UUID.randomUUID() + "@example.com";
        return jdbc.sql("insert into customer (email, name) values (:email, 'Pat') returning id")
                .param("email", email).query(Long.class).single();
    }

    private SimpleMailMessage awaitMail(String subjectStart) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> mail.sentTo(email).stream()
                .anyMatch(m -> m.getSubject() != null && m.getSubject().startsWith(subjectStart)));
        return mail.sentTo(email).stream().filter(m -> m.getSubject().startsWith(subjectStart)).findFirst().orElseThrow();
    }

    @Test
    void sendsThePasswordResetLink() {
        long id = customer();

        transactions.executeWithoutResult(s ->
                events.publishEvent(new PasswordResetRequested(id, "http://localhost:5173/reset-password?token=abc")));

        assertThat(awaitMail("Reset your Shopwise password").getText())
                .contains("http://localhost:5173/reset-password?token=abc");
    }

    @Test
    void confirmsAndCancelsOrdersByEmail() {
        long id = customer();
        long address = jdbc.sql("""
                insert into address (customer_id, recipient, line1, city, state, postal_code, is_default)
                values (:id, 'Pat', '9 Elm St', 'Springfield', 'IL', '62701', true) returning id
                """).param("id", id).query(Long.class).single();
        var cart = carts.resolve(id, null);
        long variant = jdbc.sql("select variant_id from inventory where quantity >= 2 order by variant_id limit 1")
                .query(Long.class).single();
        carts.addItem(cart, variant, 1);
        var quote = checkout.quote(id, cart, null, null);

        var order = checkout.placeOrder(id, cart,
                new PlaceOrderRequest(address, quote.shippingOption(), quote.cartHash(), UUID.randomUUID().toString()));

        var confirmation = awaitMail("Your Shopwise order #" + order.id());
        assertThat(confirmation.getText()).contains("9 Elm St").contains("/orders/" + order.id());

        orders.cancel(id, order.id());

        assertThat(awaitMail("Your Shopwise order #" + order.id() + " was cancelled").getText()).contains("refunded");
    }
}
