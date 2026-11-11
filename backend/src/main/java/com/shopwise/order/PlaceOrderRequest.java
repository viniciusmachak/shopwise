package com.shopwise.order;

/**
 * @param expectedCartHash the {@link CheckoutQuote#cartHash()} the customer agreed to
 * @param idempotencyKey   unique per checkout attempt; repeating it returns the same order
 */
public record PlaceOrderRequest(long addressId, ShippingOption shipping, String expectedCartHash, String idempotencyKey) {
}
