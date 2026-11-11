package com.shopwise.order.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.shopwise.order.OrderStatus;
import com.shopwise.order.ShippingOption;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    long customerId;

    @Enumerated(EnumType.STRING)
    OrderStatus status;

    BigDecimal subtotal;

    BigDecimal discount;

    BigDecimal shipping;

    BigDecimal total;

    String currency;

    String couponCode;

    @Enumerated(EnumType.STRING)
    ShippingOption shippingOption;

    String shipRecipient;

    String shipLine1;

    String shipLine2;

    String shipCity;

    String shipState;

    String shipPostalCode;

    String shipCountry;

    String idempotencyKey;

    Instant placedAt = Instant.now();

    Instant updatedAt = Instant.now();

    Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    Order(long customerId, String idempotencyKey) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    boolean isCancellable() {
        return status == OrderStatus.PAID;
    }

    void markPaid() {
        status = OrderStatus.PAID;
        updatedAt = Instant.now();
    }

    void cancel() {
        status = OrderStatus.CANCELLED;
        cancelledAt = Instant.now();
        updatedAt = cancelledAt;
    }
}
