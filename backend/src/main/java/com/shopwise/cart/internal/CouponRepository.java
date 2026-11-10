package com.shopwise.cart.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface CouponRepository extends JpaRepository<Coupon, String> {
}
