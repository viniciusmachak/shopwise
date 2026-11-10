package com.shopwise.catalog.internal;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_variant")
class ProductVariant {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private String sku;

    private String size;

    private String color;

    private BigDecimal priceOverride;

    @OneToOne(fetch = FetchType.LAZY)
    @PrimaryKeyJoinColumn
    private Inventory inventory;

    protected ProductVariant() {
    }

    Long getId() {
        return id;
    }

    Product getProduct() {
        return product;
    }

    String getSku() {
        return sku;
    }

    String getSize() {
        return size;
    }

    String getColor() {
        return color;
    }

    BigDecimal effectivePrice() {
        return priceOverride != null ? priceOverride : product.getPrice();
    }

    int stock() {
        return inventory == null ? 0 : inventory.getQuantity();
    }
}
