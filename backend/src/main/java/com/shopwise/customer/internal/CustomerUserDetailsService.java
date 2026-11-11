package com.shopwise.customer.internal;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customers;

    CustomerUserDetailsService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return customers.findByEmail(email)
                .map(CustomerUserDetails::of)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown e-mail"));
    }
}
