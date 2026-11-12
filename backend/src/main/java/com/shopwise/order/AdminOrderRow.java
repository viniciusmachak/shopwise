package com.shopwise.order;

import java.time.Instant;

import com.shopwise.catalog.Money;

public record AdminOrderRow(long id, OrderStatus status, Instant placedAt, String customerName, String customerEmail,
        int itemCount, Money total) {
}
