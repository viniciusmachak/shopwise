package com.shopwise.cart.internal;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon")
class Coupon {

    @Id
    private String code;

    private int percentOff;

    private boolean active;

    private Instant expiresAt;

    protected Coupon() {
    }

    int getPercentOff() {
        return percentOff;
    }

    boolean isUsable(Instant now) {
        return active && (expiresAt == null || expiresAt.isAfter(now));
    }
}
