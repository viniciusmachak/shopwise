package com.shopwise.cart;

/**
 * Public API of the cart module. All prices are recomputed from the catalog.
 */
public interface CartService {

    int MAX_QUANTITY_PER_ITEM = 10;

    CartView view(CartKey key);

    /** Adds the variant, or increases its quantity if it's already in the cart. */
    CartView addItem(CartKey key, long variantId, int quantity);

    /** Sets the quantity of a cart item; 0 removes it. */
    CartView updateItem(CartKey key, long itemId, int quantity);

    CartView applyCoupon(CartKey key, String code);

    CartView removeCoupon(CartKey key);
}
