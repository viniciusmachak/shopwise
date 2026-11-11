package com.shopwise.catalog;

/**
 * Thrown when reserving more units than are in stock.
 */
public class OutOfStockException extends RuntimeException {

    private final long variantId;

    public OutOfStockException(long variantId) {
        super("Not enough stock for variant " + variantId);
        this.variantId = variantId;
    }

    public long variantId() {
        return variantId;
    }
}
