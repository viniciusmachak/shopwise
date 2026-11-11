package com.shopwise.customer.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("select c from Customer c where lower(c.email) = lower(:email)")
    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByGoogleSubject(String googleSubject);
}
