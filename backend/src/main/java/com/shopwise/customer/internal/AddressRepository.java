package com.shopwise.customer.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCustomerIdOrderByIdAsc(long customerId);

    Optional<Address> findByIdAndCustomerId(long id, long customerId);

    Optional<Address> findByCustomerIdAndDefaultAddressTrue(long customerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Address a set a.defaultAddress = false where a.customerId = :customerId and a.defaultAddress = true")
    void clearDefault(long customerId);
}
