package com.shopwise.order;

/**
 * A checkout or order operation that can't be done, with a message safe to show the user.
 */
public class CheckoutException extends RuntimeException {

    public enum Reason {
        EMPTY_CART, ITEM_UNAVAILABLE, NO_ADDRESS, CART_CHANGED, OUT_OF_STOCK, PAYMENT_DECLINED, NOT_FOUND, NOT_CANCELLABLE, INVALID_STATUS
    }

    private final Reason reason;

    public CheckoutException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
