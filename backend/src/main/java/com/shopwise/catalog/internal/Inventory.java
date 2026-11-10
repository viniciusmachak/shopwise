package com.shopwise.catalog.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
class Inventory {

    @Id
    private Long variantId;

    private int quantity;

    protected Inventory() {
    }

    int getQuantity() {
        return quantity;
    }
}
