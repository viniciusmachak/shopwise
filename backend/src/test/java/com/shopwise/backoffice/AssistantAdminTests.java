package com.shopwise.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.TestcontainersConfiguration;
import com.shopwise.assistant.AssistantAdmin;
import com.shopwise.assistant.ChatMode;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = { "shopwise.admin.emails=ai-admin@example.test", "shopwise.ai.chat-mode=local" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AssistantAdminTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AssistantAdmin assistant;

    Cookie admin;

    @BeforeEach
    void signIn() {
        jdbc.sql("delete from customer where email = 'ai-admin@example.test'").update();
        admin = mvc.post().uri("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ai-admin@example.test\", \"password\": \"correct horse\", \"name\": \"AI\"}")
                .exchange().getResponse().getCookie("SESSION");
        jdbc.sql("delete from app_setting").update();
        assistant.changeMode(ChatMode.LOCAL, 0);
    }

    @Test
    void switchingTheModeTakesEffectAndIsSaved() {
        assertThat(mvc.put().uri("/api/admin/assistant/mode").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\": \"CLOUD\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.mode").isEqualTo("CLOUD");

        assertThat(assistant.status().mode()).isEqualTo(ChatMode.CLOUD);
        assertThat(assistant.status().configuredMode()).isEqualTo(ChatMode.LOCAL);
        assertThat(jdbc.sql("select value from app_setting where key = 'ai.chat-mode'").query(String.class).single())
                .isEqualTo("CLOUD");
    }

    @Test
    void customersCannotSwitchTheMode() {
        var customer = mvc.post().uri("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"c-%s@example.test\", \"password\": \"correct horse\", \"name\": \"C\"}"
                        .formatted(UUID.randomUUID()))
                .exchange().getResponse().getCookie("SESSION");

        assertThat(mvc.put().uri("/api/admin/assistant/mode").cookie(customer).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\": \"CLOUD\"}")).hasStatus(403);
    }

    @Test
    void statsCountConversationsOrdersAndToolUse() {
        jdbc.sql("delete from pending_action").update();
        jdbc.sql("delete from audit_log").update();
        jdbc.sql("delete from assistant_llm_usage").update();
        jdbc.sql("delete from spring_ai_chat_memory").update();
        jdbc.sql("delete from assistant_conversation").update();
        long customer = jdbc.sql("insert into customer (email, name) values (:e, 'Pat') returning id")
                .param("e", "stats-" + UUID.randomUUID() + "@example.test").query(Long.class).single();
        var cart = UUID.randomUUID();
        var withOrder = conversation(customer, cart, "I need rain boots");
        conversation(null, UUID.randomUUID(), "hello");
        jdbc.sql("""
                insert into pending_action (token, type, customer_id, cart_key, conversation_id, expires_at, used_at, result_order_id)
                values ('t1', 'CHECKOUT', :c, :cart, :conv, now(), now(), 99)
                """).param("c", customer).param("cart", cart).param("conv", withOrder).update();
        jdbc.sql("""
                insert into audit_log (cart_key, conversation_id, action, arguments, outcome)
                values (:cart, :conv, 'addToCart', '{}', 'OK'), (:cart, :conv, 'addToCart', '{}', 'REJECTED')
                """).param("cart", cart).param("conv", withOrder).update();
        jdbc.sql("""
                insert into assistant_llm_usage (day, provider, requests, failures, prompt_tokens, completion_tokens)
                values (current_date, 'ollama', 3, 1, 1200, 300)
                """).update();

        var stats = assistant.stats(7);

        assertThat(stats.conversations()).isEqualTo(2);
        assertThat(stats.ordersPlaced()).isEqualTo(1);
        assertThat(stats.conversionRate()).isEqualTo(0.5);
        assertThat(stats.tools()).singleElement().satisfies(t -> {
            assertThat(t.ok()).isEqualTo(1);
            assertThat(t.rejected()).isEqualTo(1);
        });
        assertThat(stats.usage()).singleElement().satisfies(u -> assertThat(u.promptTokens()).isEqualTo(1200));

        var recent = assistant.recentConversations(10);
        assertThat(recent).hasSize(2);
        assertThat(recent).anySatisfy(c -> {
            assertThat(c.firstQuestion()).isEqualTo("I need rain boots");
            assertThat(c.placedOrder()).isTrue();
            assertThat(c.customer()).startsWith("stats-");
        });
        assertThat(recent).anySatisfy(c -> assertThat(c.customer()).isEqualTo("Visitor"));
    }

    private UUID conversation(Long customer, UUID cart, String question) {
        var id = UUID.randomUUID();
        jdbc.sql("insert into assistant_conversation (id, cart_key, customer_id) values (:id, :cart, :c)")
                .param("id", id).param("cart", cart).param("c", customer).update();
        jdbc.sql("""
                insert into spring_ai_chat_memory (conversation_id, content, type, "timestamp", sequence_id)
                values (:id, :q, 'USER', now(), 1), (:id, 'Sure!', 'ASSISTANT', now(), 2)
                """).param("id", id.toString()).param("q", question).update();
        return id;
    }
}
