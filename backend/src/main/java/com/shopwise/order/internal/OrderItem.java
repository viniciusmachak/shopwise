package com.shopwise.order.internal;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_item")
class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    Order order;

    long variantId;

    long productId;

    String productName;

    String size;

    String color;

    BigDecimal unitPrice;

    int quantity;

    BigDecimal lineTotal;

    protected OrderItem() {
    }

    OrderItem(Order order) {
        this.order = order;
    }
}
