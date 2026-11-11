package com.shopwise.customer.internal;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Puts a customer into the session after registration, password login or Google sign-in.
 */
@Component
class SessionAuthenticator {

    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();
    private final ChangeSessionIdAuthenticationStrategy sessionFixation = new ChangeSessionIdAuthenticationStrategy();

    void signIn(Customer customer, HttpServletRequest request, HttpServletResponse response) {
        var principal = CustomerUserDetails.of(customer);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        if (request.getSession(false) != null) {
            sessionFixation.onAuthentication(authentication, request, response);
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }
}
