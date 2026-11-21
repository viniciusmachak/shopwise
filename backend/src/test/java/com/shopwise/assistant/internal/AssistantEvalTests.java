package com.shopwise.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shopwise.TestcontainersConfiguration;
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;

/**
 * Scripted conversations against the real local model. They check which tools were
 * called and the resulting cart, never the exact wording.
 *
 * Needs Ollama on localhost:11434 with the embedding and chat models pulled.
 * Run with: ./mvnw verify -Pevals
 */
@Tag("eval")
@SpringBootTest(properties = {
        "shopwise.test.fake-embeddings=false",
        "shopwise.ai.chat-mode=local",
        "shopwise.scheduling.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class AssistantEvalTests {

    @Autowired
    AssistantService assistant;

    @Autowired
    CartService carts;

    @Autowired
    JdbcClient jdbc;

    private final CartKey cartKey = CartKey.random();
    private final UUID conversation = UUID.randomUUID();

    @BeforeEach
    void waitForEmbeddings() {
        Awaitility.await().atMost(Duration.ofMinutes(3)).until(() -> jdbc
                .sql("select count(*) from search_document where embedding is null")
                .query(Integer.class).single() == 0);
    }

    record Turn(List<String> tools, String text, List<String> errors) {
    }

    private Long customerId;

    @Autowired
    com.shopwise.order.OrderService orders;

    private long signedInCustomer() {
        customerId = jdbc.sql("insert into customer (email, name) values (:e, 'Pat') returning id")
                .param("e", "eval-" + UUID.randomUUID() + "@example.com").query(Long.class).single();
        jdbc.sql("""
                insert into address (customer_id, recipient, line1, city, state, postal_code, is_default)
                values (:id, 'Pat Doe', '123 Main St', 'Springfield', 'IL', '62701', true)
                """).param("id", customerId).update();
        jdbc.sql("insert into cart (cart_key, customer_id) values (:key, :id)")
                .param("key", cartKey.value()).param("id", customerId).update();
        return customerId;
    }

    private Turn say(String message) {
        List<ServerSentEvent<Object>> events = assistant.reply(conversation, cartKey, customerId, message, false)
                .collectList()
                .block(Duration.ofMinutes(5));
        var tools = events.stream()
                .filter(e -> "tool_status".equals(e.event()))
                .map(e -> (String) ((Map<?, ?>) e.data()).get("tool"))
                .toList();
        var text = new StringBuilder();
        events.stream().filter(e -> "token".equals(e.event()))
                .forEach(e -> text.append(((Map<?, ?>) e.data()).get("text")));
        var errors = events.stream().filter(e -> "error".equals(e.event())).map(e -> e.data().toString()).toList();
        return new Turn(tools, text.toString(), errors);
    }

    private String sizeInStock(String slug) {
        return jdbc.sql("""
                select v.size from product_variant v
                join product p on p.id = v.product_id and p.slug = :slug
                join inventory i on i.variant_id = v.id and i.quantity > 0
                order by v.id limit 1
                """).param("slug", slug).query(String.class).single();
    }

    @Test
    void searchesWhenTheUserLooksForSomething() {
        var turn = say("I'm looking for a waterproof rain jacket");

        assertThat(turn.errors()).isEmpty();
        assertThat(turn.tools()).contains("searchProducts");
        assertThat(turn.text()).isNotBlank();
    }

    @Test
    void findsAProductAndAddsTheRequestedSize() {
        var size = sizeInStock("trail-runner-x");

        say("Show me trail running shoes");
        var turn = say("Add the Trail Runner X in size " + size + " to my cart");

        assertThat(turn.errors()).isEmpty();
        assertThat(turn.tools()).contains("addToCart");
        assertThat(carts.view(cartKey).items())
                .anySatisfy(line -> {
                    assertThat(line.productName()).isEqualTo("Trail Runner X");
                    assertThat(line.size()).isEqualTo(size);
                });
    }

    @Test
    void appliesACoupon() {
        var size = sizeInStock("trail-runner-x");
        say("Add the Trail Runner X in size " + size + " to my cart");

        var turn = say("Please apply the coupon code WELCOME10");

        assertThat(turn.tools()).contains("applyCoupon");
        assertThat(carts.view(cartKey).couponCode()).isEqualTo("WELCOME10");
    }

    @Test
    void doesNotChangeTheCartWhenAskedToPlaceAnOrder() {
        var turn = say("Place my order now");

        assertThat(turn.errors()).isEmpty();
        assertThat(turn.tools()).doesNotContain("addToCart", "updateCartItem", "applyCoupon");
        assertThat(carts.view(cartKey).items()).isEmpty();
    }

    @Test
    void checksOutOnlyAfterTheUserConfirms() {
        long customer = signedInCustomer();
        var size = sizeInStock("trail-runner-x");
        say("Add the Trail Runner X in size " + size + " to my cart");

        var prepare = say("I'd like to check out");

        assertThat(prepare.tools()).contains("prepareCheckout").doesNotContain("confirmCheckout");
        assertThat(orders.list(customer)).isEmpty();

        var confirm = say("Yes, place the order");

        assertThat(confirm.tools()).contains("confirmCheckout");
        assertThat(orders.list(customer)).hasSize(1);
    }

    @Test
    void looksUpOrders() {
        signedInCustomer();

        var turn = say("Where are my orders?");

        assertThat(turn.errors()).isEmpty();
        assertThat(turn.tools()).containsAnyOf("listOrders", "trackOrder");
    }

    @Test
    void comparesProductsWhenAsked() {
        var turn = say("Compare the Trail Runner X and the Stormguard GTX Runner for me");

        assertThat(turn.errors()).isEmpty();
        assertThat(turn.tools()).contains("compareProducts");
    }

    @Test
    void recommendsWhatGoesWithAProduct() {
        var turn = say("What else would go well with the Ridge Mid WP boots?");

        assertThat(turn.errors()).isEmpty();
        assertThat(turn.tools()).contains("recommendProducts");
    }
}
