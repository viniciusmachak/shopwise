package com.shopwise.assistant.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.shopwise.cart.CartKey;
import com.shopwise.order.CheckoutException;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderService;
import com.shopwise.order.OrderStatus;
import com.shopwise.order.OrderView;
import com.shopwise.order.ShippingOption;

/**
 * Checkout and order tools. Placing and cancelling orders always goes through a
 * prepare step that shows the customer a summary and a confirm step with its token.
 * The customer comes from the request's security context, never from the model.
 */
class OrderTools {

    private final @Nullable Long customerId;
    private final CartKey cartKey;
    private final UUID conversationId;
    private final UiEvents ui;
    private final CheckoutService checkout;
    private final OrderService orders;
    private final PendingActions pending;
    private final AuditLog audit;

    OrderTools(@Nullable Long customerId, CartKey cartKey, UUID conversationId, UiEvents ui, CheckoutService checkout,
            OrderService orders, PendingActions pending, AuditLog audit) {
        this.customerId = customerId;
        this.cartKey = cartKey;
        this.conversationId = conversationId;
        this.ui = ui;
        this.checkout = checkout;
        this.orders = orders;
        this.pending = pending;
        this.audit = audit;
    }

    @Tool(description = """
            Start checking out the cart. Returns a summary and a confirmation token, and shows the user a Confirm button.
            Always tell the user the total and ask them to confirm. Never place the order in the same turn.""")
    Object prepareCheckout(
            @ToolParam(required = false, description = "standard or express, default standard") String shipping) {
        status("prepareCheckout");
        if (customerId == null) {
            return signInRequired("check out");
        }
        var option = "express".equalsIgnoreCase(shipping) ? ShippingOption.EXPRESS : ShippingOption.STANDARD;
        var quote = checkout.quote(customerId, cartKey, null, option);
        if (quote.items().isEmpty()) {
            return new ShopTools.ToolError("The cart is empty.");
        }
        if (quote.address() == null) {
            return new ShopTools.ToolError(
                    "There's no saved shipping address. The user can add one on their account page, then try again.");
        }
        var prepared = pending.prepareCheckout(customerId, cartKey, conversationId, quote);
        audit.record(cartKey.value(), conversationId, "prepareCheckout", Map.of("shipping", option.name()), true, null);
        ui.emit("ui.confirm", Map.of(
                "token", prepared.token(),
                "action", "checkout",
                "summary", quote,
                "expiresAt", prepared.expiresAt()));
        return new CheckoutSummary(prepared.token(),
                quote.items().stream().mapToInt(l -> l.quantity()).sum(),
                quote.total().amount(), quote.shipping().amount(), option.name().toLowerCase(),
                quote.address().shortLabel(),
                "Ask the user to confirm. Call confirmCheckout only after they clearly say yes.");
    }

    @Tool(description = "Place the order the user just confirmed. Only call it after the user clearly said yes to the checkout summary.")
    Object confirmCheckout(@ToolParam(description = "The token returned by prepareCheckout") String token) {
        return confirm("confirmCheckout", token);
    }

    @Tool(description = "List the user's recent orders.")
    Object listOrders() {
        status("listOrders");
        if (customerId == null) {
            return signInRequired("see orders");
        }
        return new OrderList(orders.list(customerId).stream().limit(10)
                .map(o -> new OrderBrief(o.id(), o.status(), o.placedAt(), o.itemCount(), o.total().amount()))
                .toList());
    }

    @Tool(description = "Get the status and contents of one of the user's orders.")
    Object trackOrder(@ToolParam(description = "Order number") long orderId) {
        status("trackOrder");
        if (customerId == null) {
            return signInRequired("see orders");
        }
        return orders.find(customerId, orderId)
                .<Object>map(order -> {
                    ui.emit("ui.order", order);
                    return OrderInfo.of(order);
                })
                .orElse(new ShopTools.ToolError("There's no order #" + orderId + " on this account."));
    }

    @Tool(description = """
            Start cancelling an order. Returns a confirmation token and shows the user a Confirm button.
            Ask the user to confirm. Never cancel in the same turn.""")
    Object prepareCancel(@ToolParam(description = "Order number") long orderId) {
        status("prepareCancel");
        if (customerId == null) {
            return signInRequired("cancel orders");
        }
        var order = orders.find(customerId, orderId);
        if (order.isEmpty()) {
            return new ShopTools.ToolError("There's no order #" + orderId + " on this account.");
        }
        if (!order.get().cancellable()) {
            return new ShopTools.ToolError("Order #" + orderId + " can't be cancelled because it is "
                    + order.get().status().name().toLowerCase() + ".");
        }
        var prepared = pending.prepareCancel(customerId, cartKey, conversationId, orderId);
        audit.record(cartKey.value(), conversationId, "prepareCancel", Map.of("orderId", orderId), true, null);
        ui.emit("ui.confirm", Map.of(
                "token", prepared.token(),
                "action", "cancel",
                "summary", order.get(),
                "expiresAt", prepared.expiresAt()));
        return new CancelSummary(prepared.token(), orderId, order.get().total().amount(),
                "Ask the user to confirm. Call confirmCancel only after they clearly say yes.");
    }

    @Tool(description = "Cancel the order the user just confirmed. Only call it after the user clearly said yes.")
    Object confirmCancel(@ToolParam(description = "The token returned by prepareCancel") String token) {
        return confirm("confirmCancel", token);
    }

    private Object confirm(String tool, String token) {
        status(tool);
        if (customerId == null) {
            return signInRequired("continue");
        }
        try {
            var confirmed = pending.confirm(token == null ? "" : token.trim(), customerId);
            audit.record(cartKey.value(), conversationId, tool, Map.of("orderId", confirmed.order().id()), true, null);
            ui.emit("ui.order", confirmed.order());
            return OrderInfo.of(confirmed.order());
        }
        catch (PendingActionException | CheckoutException e) {
            audit.record(cartKey.value(), conversationId, tool, Map.of(), false, e.getMessage());
            return new ShopTools.ToolError(e.getMessage());
        }
    }

    private ShopTools.ToolError signInRequired(String what) {
        ui.emit("ui.signin", Map.of("reason", what));
        return new ShopTools.ToolError("The user needs to sign in to " + what + ". A sign-in button is shown to them.");
    }

    private void status(String tool) {
        ui.emit("tool_status", Map.of("tool", tool, "state", "running"));
    }

    record CheckoutSummary(String token, int items, BigDecimal total, BigDecimal shipping, String shippingSpeed,
            String shipTo, String next) {
    }

    record CancelSummary(String token, long orderId, BigDecimal refund, String next) {
    }

    record OrderBrief(long orderId, OrderStatus status, Instant placedAt, int items, BigDecimal total) {
    }

    record OrderList(List<OrderBrief> orders) {
    }

    record OrderInfo(long orderId, OrderStatus status, Instant placedAt, List<String> items, BigDecimal total,
            String shipTo, boolean cancellable) {

        static OrderInfo of(OrderView o) {
            return new OrderInfo(o.id(), o.status(), o.placedAt(),
                    o.items().stream().map(i -> i.quantity() + " x " + i.productName()
                            + (i.size() == null ? "" : " (size " + i.size() + ")")).toList(),
                    o.total().amount(), o.shippingAddress().shortLabel(), o.cancellable());
        }
    }
}
