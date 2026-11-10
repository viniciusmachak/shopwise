package com.shopwise.cart;

/**
 * A cart operation that can't be done, with a message safe to show the user.
 */
public class CartException extends RuntimeException {

    public enum Reason { VARIANT_NOT_FOUND, ITEM_NOT_FOUND, OUT_OF_STOCK, INVALID_QUANTITY, INVALID_COUPON }

    private final Reason reason;

    public CartException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
