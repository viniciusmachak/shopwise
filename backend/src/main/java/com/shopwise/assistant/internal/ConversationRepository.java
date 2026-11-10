package com.shopwise.assistant.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Binds each conversation to the cart that started it, so nobody can read or
 * continue someone else's conversation by guessing its id.
 */
@Component
class ConversationRepository {

    private final JdbcClient jdbc;

    ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True if the conversation belongs to this cart (creating it if new). */
    boolean claim(UUID conversationId, UUID cartKey) {
        jdbc.sql("insert into assistant_conversation (id, cart_key) values (:id, :cartKey) on conflict (id) do nothing")
                .param("id", conversationId)
                .param("cartKey", cartKey)
                .update();
        Optional<UUID> owner = jdbc.sql("select cart_key from assistant_conversation where id = :id")
                .param("id", conversationId)
                .query(UUID.class)
                .optional();
        return owner.map(cartKey::equals).orElse(false);
    }
}
