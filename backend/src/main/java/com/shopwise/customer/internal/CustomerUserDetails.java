package com.shopwise.customer.internal;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.shopwise.customer.CurrentCustomer.CustomerPrincipal;

/**
 * Principal stored in the session. Keeps only the id, e-mail and role.
 */
record CustomerUserDetails(long customerId, String email, String passwordHash, String role)
        implements UserDetails, CustomerPrincipal {

    @Serial
    private static final long serialVersionUID = 1L;

    static CustomerUserDetails of(Customer customer) {
        return new CustomerUserDetails(customer.getId(), customer.getEmail(),
                customer.getPasswordHash() == null ? "" : customer.getPasswordHash(), customer.getRole().name());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return !passwordHash.isEmpty();
    }
}
