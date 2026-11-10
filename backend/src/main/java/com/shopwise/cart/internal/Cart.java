package com.shopwise.cart.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "cart")
class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID cartKey;

    private String couponCode;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    Cart(UUID cartKey) {
        this.cartKey = cartKey;
    }

    List<CartItem> getItems() {
        return items;
    }

    String getCouponCode() {
        return couponCode;
    }

    Optional<CartItem> findItem(long itemId) {
        return items.stream().filter(i -> i.getId() != null && i.getId() == itemId).findFirst();
    }

    Optional<CartItem> findByVariant(long variantId) {
        return items.stream().filter(i -> i.getVariantId() == variantId).findFirst();
    }

    void add(long variantId, int quantity) {
        items.add(new CartItem(this, variantId, quantity));
        touch();
    }

    void remove(CartItem item) {
        items.remove(item);
        touch();
    }

    void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
        touch();
    }

    void touch() {
        updatedAt = Instant.now();
    }
}
