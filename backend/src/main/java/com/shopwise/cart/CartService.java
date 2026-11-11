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

    /** Empties the cart, e.g. after an order is placed. */
    void clear(CartKey key);

    /**
     * Picks the cart for this request. Signed-in customers always get their own cart; an
     * anonymous cart from the cookie is merged into it on sign-in. Anonymous visitors never
     * get a cart that belongs to a customer (e.g. after signing out on a shared browser).
     *
     * @param customerId the signed-in customer, if any
     * @param cookieKey  the key from the cart cookie, if any
     */
    CartKey resolve(Long customerId, CartKey cookieKey);
}
