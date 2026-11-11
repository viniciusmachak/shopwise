package com.shopwise.customer.internal;

import org.springframework.http.HttpStatus;

class AccountException extends RuntimeException {

    private final HttpStatus status;

    AccountException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }
}
