package com.shopwise.catalog;

import java.util.List;

/**
 * Stock reservations for orders. Runs inside the caller's transaction.
 */
public interface StockService {

    /** Takes units out of stock, all or nothing. */
    void reserve(List<StockChange> changes) throws OutOfStockException;

    /** Puts units back, e.g. when an order is cancelled. */
    void release(List<StockChange> changes);
}
