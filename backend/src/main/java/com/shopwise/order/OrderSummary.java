package com.shopwise.order;

import java.time.Instant;

import com.shopwise.catalog.Money;

public record OrderSummary(long id, OrderStatus status, Instant placedAt, int itemCount, Money total) {
}
