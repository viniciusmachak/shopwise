package com.shopwise.customer;

import org.jspecify.annotations.Nullable;

public record AddressView(
        long id,
        String recipient,
        String line1,
        @Nullable String line2,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault) {

    /** A short, non-identifying label such as "Main St, Springfield", safe to show the assistant. */
    public String shortLabel() {
        var street = line1.replaceFirst("^\\d+\\s+", "");
        return street + ", " + city;
    }
}
