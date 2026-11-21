package com.shopwise.assistant.internal;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.cart.CartKey;
import com.shopwise.order.CheckoutQuote;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderService;
import com.shopwise.order.OrderView;
import com.shopwise.order.PlaceOrderRequest;
import com.shopwise.order.ShippingOption;

/**
 * Two-step confirmation for irreversible actions (placing and cancelling orders).
 * {@code prepare*} stores what will happen under a random single-use token; only
 * {@link #confirm} with that token, by the same customer, carries it out.
 */
@Component
class PendingActions {

    static final Duration TTL = Duration.ofMinutes(10);

    enum Type { CHECKOUT, CANCEL_ORDER }

    record Prepared(String token, Instant expiresAt) {
    }

    record ConfirmedAction(Type type, OrderView order) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final CheckoutService checkout;
    private final OrderService orders;

    PendingActions(JdbcClient jdbc, CheckoutService checkout, OrderService orders) {
        this.jdbc = jdbc;
        this.checkout = checkout;
        this.orders = orders;
    }

    Prepared prepareCheckout(long customerId, CartKey cartKey, @Nullable UUID conversationId, CheckoutQuote quote) {
        var prepared = newToken();
        jdbc.sql("""
                insert into pending_action (token, type, customer_id, cart_key, conversation_id, address_id, shipping, cart_hash, expires_at)
                values (:token, 'CHECKOUT', :customer, :cart, :conversation, :address, :shipping, :hash, :expires)
                """)
                .param("token", prepared.token())
                .param("customer", customerId)
                .param("cart", cartKey.value())
                .param("conversation", conversationId)
                .param("address", quote.address() == null ? null : quote.address().id())
                .param("shipping", quote.shippingOption().name())
                .param("hash", quote.cartHash())
                .param("expires", Timestamp.from(prepared.expiresAt()))
                .update();
        return prepared;
    }

    Prepared prepareCancel(long customerId, CartKey cartKey, @Nullable UUID conversationId, long orderId) {
        var prepared = newToken();
        jdbc.sql("""
                insert into pending_action (token, type, customer_id, cart_key, conversation_id, order_id, expires_at)
                values (:token, 'CANCEL_ORDER', :customer, :cart, :conversation, :order, :expires)
                """)
                .param("token", prepared.token())
                .param("customer", customerId)
                .param("cart", cartKey.value())
                .param("conversation", conversationId)
                .param("order", orderId)
                .param("expires", Timestamp.from(prepared.expiresAt()))
                .update();
        return prepared;
    }

    /**
     * Carries out the action. The row is locked while it runs, so a double confirmation
     * waits and then gets the same result. If the action fails, the token stays usable.
     */
    @Transactional
    ConfirmedAction confirm(String token, long customerId) {
        var action = jdbc.sql("""
                select type, cart_key, address_id, shipping, cart_hash, order_id, result_order_id, used_at, discarded_at,
                       expires_at < now() as expired
                from pending_action where token = :token and customer_id = :customer
                for update
                """)
                .param("token", token)
                .param("customer", customerId)
                .query(Row.class)
                .optional()
                .orElseThrow(() -> new PendingActionException("This confirmation isn't valid."));

        var type = Type.valueOf(action.type());
        if (action.usedAt() != null && action.resultOrderId() != null) {
            return new ConfirmedAction(type, orders.find(customerId, action.resultOrderId()).orElseThrow());
        }
        if (action.discardedAt() != null) {
            throw new PendingActionException("This action was cancelled. Ask again if you still want it.");
        }
        if (action.expired()) {
            throw new PendingActionException("This confirmation expired. Ask again to get a fresh summary.");
        }

        var order = switch (type) {
            case CHECKOUT -> checkout.placeOrder(customerId, new CartKey(action.cartKey()),
                    new PlaceOrderRequest(action.addressId(), ShippingOption.valueOf(action.shipping()),
                            action.cartHash(), "assistant-" + token));
            case CANCEL_ORDER -> orders.cancel(customerId, action.orderId());
        };
        jdbc.sql("update pending_action set used_at = now(), result_order_id = :order where token = :token")
                .param("order", order.id())
                .param("token", token)
                .update();
        return new ConfirmedAction(type, order);
    }

    /**
     * The newest unused, unexpired action of this type that was shown in this conversation.
     * The model doesn't keep tool results between turns, so it can't repeat the token itself.
     */
    Optional<String> latestShown(long customerId, UUID conversationId, Type type) {
        return jdbc.sql("""
                select token from pending_action
                where customer_id = :customer and conversation_id = :conversation and type = :type
                  and used_at is null and discarded_at is null and expires_at > now()
                order by created_at desc limit 1
                """)
                .param("customer", customerId)
                .param("conversation", conversationId)
                .param("type", type.name())
                .query(String.class)
                .optional();
    }

    /** What is waiting for the customer's confirmation in this conversation, newest first. */
    List<Waiting> waiting(long customerId, UUID conversationId) {
        return jdbc.sql("""
                select type, order_id from pending_action
                where customer_id = :customer and conversation_id = :conversation
                  and used_at is null and discarded_at is null and expires_at > now()
                order by created_at desc
                """)
                .param("customer", customerId)
                .param("conversation", conversationId)
                .query((rs, n) -> new Waiting(Type.valueOf(rs.getString("type")), (Long) rs.getObject("order_id")))
                .list();
    }

    record Waiting(Type type, Long orderId) {
    }

    void discard(String token, long customerId) {
        jdbc.sql("update pending_action set discarded_at = now() where token = :token and customer_id = :customer and used_at is null")
                .param("token", token)
                .param("customer", customerId)
                .update();
    }

    private static Prepared newToken() {
        var bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return new Prepared(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), Instant.now().plus(TTL));
    }

    record Row(String type, UUID cartKey, Long addressId, String shipping, String cartHash, Long orderId,
            Long resultOrderId, Instant usedAt, Instant discardedAt, boolean expired) {
    }
}
