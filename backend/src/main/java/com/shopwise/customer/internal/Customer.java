package com.shopwise.customer.internal;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
class Customer {

    enum Role { CUSTOMER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String name;

    private String passwordHash;

    private String googleSubject;

    @Enumerated(EnumType.STRING)
    private Role role = Role.CUSTOMER;

    private Instant createdAt = Instant.now();

    protected Customer() {
    }

    Customer(String email, String name) {
        this.email = email;
        this.name = name;
    }

    Long getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getName() {
        return name;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getGoogleSubject() {
        return googleSubject;
    }

    Role getRole() {
        return role;
    }

    void rename(String name) {
        this.name = name;
    }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    void linkGoogle(String subject) {
        this.googleSubject = subject;
    }
}
