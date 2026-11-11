package com.shopwise.order.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.cart.CartView;
import com.shopwise.catalog.Money;
import com.shopwise.catalog.OutOfStockException;
import com.shopwise.catalog.StockChange;
import com.shopwise.catalog.StockService;
import com.shopwise.customer.AddressView;
import com.shopwise.customer.CustomerDirectory;
import com.shopwise.order.CheckoutException;
import com.shopwise.order.CheckoutException.Reason;
import com.shopwise.order.CheckoutQuote;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderCancelled;
import com.shopwise.order.OrderPlaced;
import com.shopwise.order.OrderService;
import com.shopwise.order.OrderSummary;
import com.shopwise.order.OrderView;
import com.shopwise.order.PlaceOrderRequest;
import com.shopwise.order.ShippingOption;
import com.shopwise.payment.PaymentService;

@Service
@Transactional
class DefaultOrderService implements CheckoutService, OrderService {

    private final OrderRepository orders;
    private final CartService carts;
    private final CustomerDirectory customers;
    private final StockService stock;
    private final PaymentService payments;
    private final ShippingRates shippingRates;
    private final ApplicationEventPublisher events;

    DefaultOrderService(OrderRepository orders, CartService carts, CustomerDirectory customers, StockService stock,
            PaymentService payments, ShippingRates shippingRates, ApplicationEventPublisher events) {
        this.orders = orders;
        this.carts = carts;
        this.customers = customers;
        this.stock = stock;
        this.payments = payments;
        this.shippingRates = shippingRates;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutQuote quote(long customerId, CartKey cartKey, @Nullable Long addressId,
            @Nullable ShippingOption shipping) {
        var cart = carts.view(cartKey);
        var address = addressId == null
                ? customers.defaultAddress(customerId)
                : customers.address(customerId, addressId);
        return quote(cart, address.orElse(null), shipping == null ? ShippingOption.STANDARD : shipping);
    }

    private CheckoutQuote quote(CartView cart, @Nullable AddressView address, ShippingOption option) {
        var merchandise = cart.total().amount();
        var shipping = shippingRates.price(option, merchandise);
        var total = Money.usd(merchandise.add(shipping.amount()));
        var lines = cart.items().stream()
                .map(l -> new CheckoutQuote.Line(l.productName(), l.size(), l.quantity(), l.unitPrice(), l.lineTotal()))
                .toList();
        return new CheckoutQuote(lines, cart.subtotal(), cart.couponCode(), cart.discount(), option, shipping, total,
                shippingRates.choices(merchandise), address, cartHash(cart));
    }

    @Override
    public OrderView placeOrder(long customerId, CartKey cartKey, PlaceOrderRequest request) {
        var existing = orders.findByCustomerIdAndIdempotencyKey(customerId, request.idempotencyKey());
        if (existing.isPresent()) {
            return toView(existing.get());
        }

        var cart = carts.view(cartKey);
        if (cart.items().isEmpty()) {
            throw new CheckoutException(Reason.EMPTY_CART, "Your cart is empty.");
        }
        if (cart.items().stream().anyMatch(l -> !l.available())) {
            throw new CheckoutException(Reason.ITEM_UNAVAILABLE,
                    "Some items in your cart are no longer available in that quantity.");
        }
        if (!cartHash(cart).equals(request.expectedCartHash())) {
            throw new CheckoutException(Reason.CART_CHANGED,
                    "Your cart changed since you reviewed it. Please check the new total.");
        }
        var address = customers.address(customerId, request.addressId())
                .orElseThrow(() -> new CheckoutException(Reason.NO_ADDRESS, "Please choose a shipping address."));
        var quote = quote(cart, address, request.shipping());

        try {
            stock.reserve(cart.items().stream().map(l -> new StockChange(l.variantId(), l.quantity())).toList());
        }
        catch (OutOfStockException e) {
            throw new CheckoutException(Reason.OUT_OF_STOCK,
                    "Some items just sold out. Please review your cart.");
        }

        var order = new Order(customerId, request.idempotencyKey());
        fill(order, cart, quote, address);
        orders.saveAndFlush(order);

        var payment = payments.charge(order.id, quote.total());
        if (!payment.approved()) {
            // Rolls back the order and the stock reservation.
            throw new CheckoutException(Reason.PAYMENT_DECLINED,
                    payment.declineReason() == null ? "The payment was declined." : payment.declineReason());
        }
        order.markPaid();
        carts.clear(cartKey);
        events.publishEvent(new OrderPlaced(order.id, customerId));
        return toView(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummary> list(long customerId) {
        return orders.findByCustomerIdOrderByPlacedAtDesc(customerId).stream()
                .map(o -> new OrderSummary(o.id, o.status, o.placedAt,
                        o.items.stream().mapToInt(i -> i.quantity).sum(), new Money(o.total, o.currency)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderView> find(long customerId, long orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId).map(this::toView);
    }

    @Override
    public OrderView cancel(long customerId, long orderId) {
        var order = orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new CheckoutException(Reason.NOT_FOUND, "Order not found."));
        if (!order.isCancellable()) {
            throw new CheckoutException(Reason.NOT_CANCELLABLE,
                    "This order can't be cancelled anymore because it is " + order.status.name().toLowerCase() + ".");
        }
        order.cancel();
        stock.release(order.items.stream().map(i -> new StockChange(i.variantId, i.quantity)).toList());
        payments.refund(order.id);
        events.publishEvent(new OrderCancelled(order.id, customerId));
        return toView(order);
    }

    private static void fill(Order order, CartView cart, CheckoutQuote quote, AddressView address) {
        order.subtotal = quote.subtotal().amount();
        order.discount = quote.discount().amount();
        order.shipping = quote.shipping().amount();
        order.total = quote.total().amount();
        order.currency = quote.total().currency();
        order.couponCode = quote.couponCode();
        order.shippingOption = quote.shippingOption();
        order.shipRecipient = address.recipient();
        order.shipLine1 = address.line1();
        order.shipLine2 = address.line2();
        order.shipCity = address.city();
        order.shipState = address.state();
        order.shipPostalCode = address.postalCode();
        order.shipCountry = address.country();
        for (var line : cart.items()) {
            var item = new OrderItem(order);
            item.variantId = line.variantId();
            item.productId = line.productId();
            item.productName = line.productName();
            item.size = line.size();
            item.color = line.color();
            item.unitPrice = line.unitPrice().amount();
            item.quantity = line.quantity();
            item.lineTotal = line.lineTotal().amount();
            order.items.add(item);
        }
    }

    private OrderView toView(Order o) {
        var items = o.items.stream()
                .map(i -> new OrderView.Line(i.productId, i.productName, i.size, i.color, i.quantity,
                        new Money(i.unitPrice, o.currency), new Money(i.lineTotal, o.currency)))
                .toList();
        var address = new OrderView.ShippingAddress(o.shipRecipient, o.shipLine1, o.shipLine2, o.shipCity, o.shipState,
                o.shipPostalCode, o.shipCountry);
        return new OrderView(o.id, o.status, o.placedAt, o.cancelledAt, items, new Money(o.subtotal, o.currency),
                o.couponCode, new Money(o.discount, o.currency), o.shippingOption, new Money(o.shipping, o.currency),
                new Money(o.total, o.currency), address, o.isCancellable());
    }

    /** Fingerprint of what the customer is buying and at what price. */
    static String cartHash(CartView cart) {
        var text = new StringBuilder();
        cart.items().forEach(l -> text.append(l.variantId()).append(':').append(l.quantity()).append(':')
                .append(l.unitPrice().amount().toPlainString()).append(';'));
        text.append(cart.couponCode()).append(':').append(cart.discount().amount().toPlainString());
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
