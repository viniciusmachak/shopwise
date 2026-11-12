package com.shopwise.order;

/**
 * An order with its customer, for the backoffice.
 */
public record AdminOrderView(OrderView order, long customerId, String customerName, String customerEmail) {
}
