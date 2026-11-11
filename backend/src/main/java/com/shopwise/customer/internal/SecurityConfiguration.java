package com.shopwise.customer.internal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountService accounts, SessionAuthenticator sessions,
            WebProperties web, ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/me/**", "/api/checkout/**", "/api/orders/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.spa())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth.successHandler(new GoogleSignInSuccessHandler(accounts, sessions, web))
                    .failureUrl(web.frontendUrl() + "/sign-in?error=google"));
        }
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(CustomerUserDetailsService userDetails, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetails);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
