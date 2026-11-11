package com.shopwise.customer.internal;


import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopwise.customer.CurrentCustomer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AccountService accounts;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticator sessions;
    private final LoginThrottle throttle;
    private final boolean googleEnabled;

    AuthController(AccountService accounts, AuthenticationManager authenticationManager, SessionAuthenticator sessions,
            LoginThrottle throttle, ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.accounts = accounts;
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
        this.throttle = throttle;
        this.googleEnabled = clientRegistrations.getIfAvailable() != null;
    }

    /** Session state for the SPA. Also makes sure the CSRF cookie is set. */
    @GetMapping("/session")
    SessionView session(CsrfToken csrf) {
        csrf.getToken();
        var me = CurrentCustomer.get().map(ref -> MeView.of(accounts.get(ref.id()))).orElse(null);
        return new SessionView(me != null, me, googleEnabled);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    MeView register(@Valid @RequestBody RegisterForm form, HttpServletRequest request, HttpServletResponse response) {
        var customer = accounts.register(form.email(), form.password(), form.name());
        sessions.signIn(customer, request, response);
        return MeView.of(customer);
    }

    @PostMapping("/login")
    MeView login(@Valid @RequestBody LoginForm form, HttpServletRequest request, HttpServletResponse response) {
        if (throttle.isBlocked(form.email())) {
            throw new AccountException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please wait a few minutes or reset your password.");
        }
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(form.email().trim(), form.password()));
            var principal = (CustomerUserDetails) authentication.getPrincipal();
            throttle.recordSuccess(form.email());
            var customer = accounts.get(principal.customerId());
            sessions.signIn(customer, request, response);
            return MeView.of(customer);
        }
        catch (AuthenticationException e) {
            throttle.recordFailure(form.email());
            throw new AccountException(HttpStatus.UNAUTHORIZED, "Wrong e-mail or password.");
        }
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestReset(@Valid @RequestBody ResetRequestForm form) {
        accounts.requestPasswordReset(form.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmReset(@Valid @RequestBody ResetConfirmForm form) {
        accounts.resetPassword(form.token(), form.password());
    }

    @ExceptionHandler
    ProblemDetail handle(AccountException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    record SessionView(boolean authenticated, MeView customer, boolean googleEnabled) {
    }

    record RegisterForm(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 120) String name) {
    }

    record LoginForm(@NotBlank String email, @NotBlank String password) {
    }

    record ResetRequestForm(@NotBlank @Email String email) {
    }

    record ResetConfirmForm(@NotBlank String token, @NotBlank @Size(max = 200) String password) {
    }

}
