package com.shopwise.customer;

import java.util.List;
import java.util.Optional;

/**
 * Public API of the customer module for other modules.
 */
public interface CustomerDirectory {

    Optional<CustomerContact> contact(long customerId);

    List<AddressView> addresses(long customerId);

    Optional<AddressView> address(long customerId, long addressId);

    Optional<AddressView> defaultAddress(long customerId);
}
