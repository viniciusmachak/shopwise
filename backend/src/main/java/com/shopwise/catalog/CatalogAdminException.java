package com.shopwise.catalog;

public class CatalogAdminException extends RuntimeException {

    public enum Reason { NOT_FOUND, UNKNOWN_CATEGORY, DUPLICATE_SKU, INVALID_STOCK }

    private final Reason reason;

    public CatalogAdminException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
