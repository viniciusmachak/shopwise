package com.shopwise.customer.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record AddressForm(
        @NotBlank @Size(max = 120) String recipient,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 20) String postalCode,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a two-letter country code") String country) {
}
