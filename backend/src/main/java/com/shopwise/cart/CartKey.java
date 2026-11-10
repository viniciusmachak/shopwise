package com.shopwise.cart;

import java.util.UUID;

/**
 * Identifies a cart. Resolved on the server from the cart cookie; never taken
 * from user input or model output. Controllers can declare it as a parameter.
 */
public record CartKey(UUID value) {

    public static CartKey random() {
        return new CartKey(UUID.randomUUID());
    }
}
