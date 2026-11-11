package com.shopwise.customer.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopwise.customer.AddressView;
import com.shopwise.customer.CurrentCustomer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/me")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    MeView me() {
        return MeView.of(accounts.get(CurrentCustomer.require().id()));
    }

    @PatchMapping
    MeView update(@Valid @RequestBody ProfileForm form) {
        return MeView.of(accounts.rename(CurrentCustomer.require().id(), form.name()));
    }

    @GetMapping("/addresses")
    List<AddressView> addresses() {
        return accounts.addresses(CurrentCustomer.require().id());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    AddressView addAddress(@Valid @RequestBody AddressForm form) {
        return accounts.addAddress(CurrentCustomer.require().id(), form);
    }

    @PutMapping("/addresses/{id}")
    AddressView updateAddress(@PathVariable long id, @Valid @RequestBody AddressForm form) {
        return accounts.updateAddress(CurrentCustomer.require().id(), id, form);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAddress(@PathVariable long id) {
        accounts.deleteAddress(CurrentCustomer.require().id(), id);
    }

    @PostMapping("/addresses/{id}/default")
    AddressView makeDefault(@PathVariable long id) {
        return accounts.makeDefault(CurrentCustomer.require().id(), id);
    }

    @ExceptionHandler
    ProblemDetail handle(AccountException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    record ProfileForm(@NotBlank @Size(max = 120) String name) {
    }
}
