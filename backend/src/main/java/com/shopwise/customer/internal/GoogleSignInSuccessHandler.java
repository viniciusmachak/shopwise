package com.shopwise.customer.internal;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After Google sign-in, replaces the OIDC principal with our own customer principal
 * and sends the browser back to the store.
 */
class GoogleSignInSuccessHandler implements AuthenticationSuccessHandler {

    private final AccountService accounts;
    private final SessionAuthenticator sessions;
    private final WebProperties web;

    GoogleSignInSuccessHandler(AccountService accounts, SessionAuthenticator sessions, WebProperties web) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.web = web;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser user)) {
            response.sendRedirect(web.frontendUrl() + "/sign-in?error=google");
            return;
        }
        try {
            var customer = accounts.signInWithGoogle(user.getSubject(), user.getEmail(),
                    Boolean.TRUE.equals(user.getEmailVerified()), user.getFullName());
            sessions.signIn(customer, request, response);
            response.sendRedirect(web.frontendUrl() + "/");
        }
        catch (AccountException e) {
            response.sendRedirect(web.frontendUrl() + "/sign-in?error=google");
        }
    }
}
