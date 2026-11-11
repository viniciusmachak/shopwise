package com.shopwise.customer.internal;

import java.time.Instant;

import com.shopwise.customer.AddressView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "address")
class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long customerId;

    private String recipient;

    private String line1;

    private String line2;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    @Column(name = "is_default")
    private boolean defaultAddress;

    private Instant createdAt = Instant.now();

    protected Address() {
    }

    Address(long customerId, AddressForm form) {
        this.customerId = customerId;
        update(form);
    }

    void update(AddressForm form) {
        this.recipient = form.recipient().trim();
        this.line1 = form.line1().trim();
        this.line2 = form.line2() == null || form.line2().isBlank() ? null : form.line2().trim();
        this.city = form.city().trim();
        this.state = form.state().trim();
        this.postalCode = form.postalCode().trim();
        this.country = form.country() == null || form.country().isBlank() ? "US" : form.country().trim().toUpperCase();
    }

    Long getId() {
        return id;
    }

    long getCustomerId() {
        return customerId;
    }

    boolean isDefaultAddress() {
        return defaultAddress;
    }

    void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    AddressView toView() {
        return new AddressView(id, recipient, line1, line2, city, state, postalCode, country, defaultAddress);
    }
}
