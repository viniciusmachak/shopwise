package com.shopwise.order;

import org.jspecify.annotations.Nullable;

import com.shopwise.cart.CartKey;

/**
 * Public API for checking out a customer's cart.
 */
public interface CheckoutService {

    /**
     * @param addressId the address to ship to, or null for the customer's default
     */
    CheckoutQuote quote(long customerId, CartKey cartKey, @Nullable Long addressId, @Nullable ShippingOption shipping);

    /** Reserves stock, charges the payment and creates the order, all or nothing. */
    OrderView placeOrder(long customerId, CartKey cartKey, PlaceOrderRequest request);
}
