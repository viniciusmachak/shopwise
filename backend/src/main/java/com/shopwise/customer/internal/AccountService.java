package com.shopwise.customer.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.customer.AddressView;
import com.shopwise.customer.CustomerContact;
import com.shopwise.customer.CustomerDirectory;
import com.shopwise.customer.CustomerRegistered;
import com.shopwise.customer.PasswordResetRequested;

@Service
@Transactional
class AccountService implements CustomerDirectory {

    static final int MIN_PASSWORD_LENGTH = 8;
    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerRepository customers;
    private final AddressRepository addresses;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final JdbcClient jdbc;
    private final WebProperties web;
    private final AdminAccess adminAccess;

    AccountService(CustomerRepository customers, AddressRepository addresses, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events, JdbcClient jdbc, WebProperties web,
            AdminAccess adminAccess) {
        this.customers = customers;
        this.addresses = addresses;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.jdbc = jdbc;
        this.web = web;
        this.adminAccess = adminAccess;
    }

    Customer register(String email, String password, String name) {
        var normalized = email.trim();
        checkPassword(password);
        if (customers.findByEmail(normalized).isPresent()) {
            throw new AccountException(HttpStatus.CONFLICT, "An account with this e-mail already exists.");
        }
        var customer = new Customer(normalized, name.trim());
        adminAccess.applyTo(customer);
        customer.setPasswordHash(passwordEncoder.encode(password));
        customers.save(customer);
        events.publishEvent(new CustomerRegistered(customer.getId()));
        return customer;
    }

    /**
     * Finds the customer for a Google sign-in: by Google subject, then by verified e-mail
     * (linking the account), otherwise creates a new account without a password.
     */
    Customer signInWithGoogle(String subject, String email, boolean emailVerified, String name) {
        var bySubject = customers.findByGoogleSubject(subject);
        if (bySubject.isPresent()) {
            return bySubject.get();
        }
        if (!emailVerified) {
            throw new AccountException(HttpStatus.FORBIDDEN, "Your Google e-mail address is not verified.");
        }
        var byEmail = customers.findByEmail(email);
        if (byEmail.isPresent()) {
            byEmail.get().linkGoogle(subject);
            return byEmail.get();
        }
        var customer = new Customer(email, name == null || name.isBlank() ? email : name);
        adminAccess.applyTo(customer);
        customer.linkGoogle(subject);
        customers.save(customer);
        events.publishEvent(new CustomerRegistered(customer.getId()));
        return customer;
    }

    @Transactional(readOnly = true)
    Customer get(long customerId) {
        return customers.findById(customerId)
                .orElseThrow(() -> new AccountException(HttpStatus.UNAUTHORIZED, "Please sign in."));
    }

    Customer rename(long customerId, String name) {
        var customer = get(customerId);
        customer.rename(name.trim());
        return customer;
    }

    /** Always succeeds from the caller's view, so it can't be used to discover accounts. */
    void requestPasswordReset(String email) {
        customers.findByEmail(email.trim()).ifPresent(customer -> {
            var token = randomToken();
            jdbc.sql("insert into password_reset_token (token_hash, customer_id, expires_at) values (:hash, :id, :expires)")
                    .param("hash", sha256(token))
                    .param("id", customer.getId())
                    .param("expires", java.sql.Timestamp.from(Instant.now().plus(RESET_TOKEN_TTL)))
                    .update();
            events.publishEvent(new PasswordResetRequested(customer.getId(),
                    web.frontendUrl() + "/reset-password?token=" + token));
        });
    }

    void resetPassword(String token, String newPassword) {
        checkPassword(newPassword);
        Optional<Long> customerId = jdbc.sql("""
                update password_reset_token set used_at = now()
                where token_hash = :hash and used_at is null and expires_at > now()
                returning customer_id
                """)
                .param("hash", sha256(token))
                .query(Long.class)
                .optional();
        var customer = customers.findById(customerId.orElseThrow(() ->
                new AccountException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired.")))
                .orElseThrow();
        customer.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    // Addresses

    @Transactional(readOnly = true)
    @Override
    public List<AddressView> addresses(long customerId) {
        return addresses.findByCustomerIdOrderByIdAsc(customerId).stream().map(Address::toView).toList();
    }

    AddressView addAddress(long customerId, AddressForm form) {
        var address = new Address(customerId, form);
        if (addresses.findByCustomerIdAndDefaultAddressTrue(customerId).isEmpty()) {
            address.setDefaultAddress(true);
        }
        return addresses.save(address).toView();
    }

    AddressView updateAddress(long customerId, long addressId, AddressForm form) {
        var address = ownAddress(customerId, addressId);
        address.update(form);
        return address.toView();
    }

    void deleteAddress(long customerId, long addressId) {
        var address = ownAddress(customerId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address);
        addresses.flush();
        if (wasDefault) {
            addresses.findByCustomerIdOrderByIdAsc(customerId).stream().findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    AddressView makeDefault(long customerId, long addressId) {
        ownAddress(customerId, addressId);
        addresses.clearDefault(customerId);
        var address = ownAddress(customerId, addressId);
        address.setDefaultAddress(true);
        return address.toView();
    }

    private Address ownAddress(long customerId, long addressId) {
        return addresses.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "Address not found."));
    }

    // CustomerDirectory

    @Transactional(readOnly = true)
    @Override
    public Optional<CustomerContact> contact(long customerId) {
        return customers.findById(customerId).map(c -> new CustomerContact(c.getId(), c.getEmail(), c.getName()));
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<AddressView> address(long customerId, long addressId) {
        return addresses.findByIdAndCustomerId(addressId, customerId).map(Address::toView);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<AddressView> defaultAddress(long customerId) {
        return addresses.findByCustomerIdAndDefaultAddressTrue(customerId).map(Address::toView);
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new AccountException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Use a password with at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private static String randomToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
