package com.shopwise.customer;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the signed-in customer from the Spring Security context of the current request.
 */
public final class CurrentCustomer {

    private CurrentCustomer() {
    }

    public static Optional<CustomerRef> get() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public static CustomerRef require() {
        return get().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in."));
    }

    public static Optional<CustomerRef> from(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomerPrincipal principal) {
            return Optional.of(new CustomerRef(principal.customerId()));
        }
        return Optional.empty();
    }

    /**
     * Implemented by the authentication principal of signed-in customers.
     */
    public interface CustomerPrincipal {

        long customerId();
    }
}
