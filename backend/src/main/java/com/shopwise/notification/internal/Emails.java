package com.shopwise.notification.internal;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.stream.Collectors;

import com.shopwise.catalog.Money;
import com.shopwise.customer.CustomerContact;
import com.shopwise.order.OrderView;

/**
 * Plain-text e-mail bodies.
 */
final class Emails {

    private Emails() {
    }

    record Email(String subject, String body) {
    }

    static Email passwordReset(CustomerContact to, String link) {
        return new Email("Reset your Shopwise password", """
                Hi %s,

                Someone asked to reset the password for your Shopwise account. If it was you, open this link
                within 30 minutes to choose a new password:

                %s

                If you didn't ask for this, you can ignore this e-mail. Your password stays the same.

                Shopwise
                """.formatted(to.name(), link));
    }

    static Email orderPlaced(CustomerContact to, OrderView order, String ordersUrl) {
        return new Email("Your Shopwise order #" + order.id(), """
                Hi %s,

                Thanks for your order. Here's what we received:

                %s

                Subtotal: %s
                Discount: %s
                Shipping: %s
                Total:    %s

                Shipping to:
                %s

                You can follow your order at %s

                Shopwise
                """.formatted(to.name(), lines(order), money(order.subtotal()), money(order.discount()),
                money(order.shipping()), money(order.total()), address(order.shippingAddress()), ordersUrl));
    }

    static Email orderCancelled(CustomerContact to, OrderView order) {
        return new Email("Your Shopwise order #" + order.id() + " was cancelled", """
                Hi %s,

                Your order #%d was cancelled. We've refunded %s to your original payment method.

                Shopwise
                """.formatted(to.name(), order.id(), money(order.total())));
    }

    private static String lines(OrderView order) {
        return order.items().stream()
                .map(l -> "- %d x %s%s  %s".formatted(l.quantity(), l.productName(),
                        l.size() == null ? "" : " (size " + l.size() + ")", money(l.lineTotal())))
                .collect(Collectors.joining("\n"));
    }

    private static String address(OrderView.ShippingAddress a) {
        var line2 = a.line2() == null ? "" : "\n" + a.line2();
        return "%s\n%s%s\n%s, %s %s\n%s".formatted(a.recipient(), a.line1(), line2, a.city(), a.state(), a.postalCode(),
                a.country());
    }

    private static String money(Money money) {
        var format = NumberFormat.getCurrencyInstance(Locale.US);
        format.setCurrency(Currency.getInstance(money.currency()));
        return format.format(money.amount());
    }
}
