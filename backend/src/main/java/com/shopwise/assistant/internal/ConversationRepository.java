package com.shopwise.assistant.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Binds each conversation to the customer (or, for visitors, the cart) that started it,
 * so nobody can read or continue someone else's conversation by guessing its id.
 */
@Component
class ConversationRepository {

    private final JdbcClient jdbc;

    ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Owner(UUID cartKey, @Nullable Long customerId) {
    }

    record ConversationSummary(UUID id, Instant createdAt, @Nullable String title) {
    }

    /**
     * True if the conversation belongs to this visitor, creating it if new. A visitor's
     * conversation is adopted by the customer when they sign in on the same cart.
     */
    boolean claim(UUID conversationId, UUID cartKey, @Nullable Long customerId) {
        jdbc.sql("""
                insert into assistant_conversation (id, cart_key, customer_id) values (:id, :cartKey, :customer)
                on conflict (id) do nothing
                """)
                .param("id", conversationId)
                .param("cartKey", cartKey)
                .param("customer", customerId)
                .update();
        var owner = jdbc.sql("select cart_key, customer_id from assistant_conversation where id = :id")
                .param("id", conversationId)
                .query(Owner.class)
                .optional();
        if (owner.isEmpty()) {
            return false;
        }
        var o = owner.get();
        if (o.customerId() != null) {
            return o.customerId().equals(customerId);
        }
        if (!o.cartKey().equals(cartKey)) {
            return false;
        }
        if (customerId != null) {
            jdbc.sql("update assistant_conversation set customer_id = :customer where id = :id and customer_id is null")
                    .param("customer", customerId)
                    .param("id", conversationId)
                    .update();
        }
        return true;
    }

    /** True if the conversation exists and belongs to this visitor; never creates it. */
    boolean owns(UUID conversationId, UUID cartKey, @Nullable Long customerId) {
        return jdbc.sql("select cart_key, customer_id from assistant_conversation where id = :id")
                .param("id", conversationId)
                .query(Owner.class)
                .optional()
                .map(o -> o.customerId() != null ? o.customerId().equals(customerId)
                        : customerId == null && o.cartKey().equals(cartKey))
                .orElse(false);
    }

    /** Conversations with at least one message, newest first, titled by their first question. */
    List<ConversationSummary> list(UUID cartKey, @Nullable Long customerId) {
        return jdbc.sql("""
                select c.id, c.created_at,
                       (select left(m.content, 80) from spring_ai_chat_memory m
                        where m.conversation_id = c.id::text and m.type = 'USER'
                        order by m.sequence_id limit 1) as title
                from assistant_conversation c
                where (cast(:customer as bigint) is not null and c.customer_id = :customer)
                   or (cast(:customer as bigint) is null and c.customer_id is null and c.cart_key = :cartKey)
                order by c.created_at desc
                limit 20
                """)
                .param("customer", customerId)
                .param("cartKey", cartKey)
                .query(ConversationSummary.class)
                .list()
                .stream()
                .filter(s -> s.title() != null)
                .toList();
    }
}
