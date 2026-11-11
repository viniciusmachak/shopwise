package com.shopwise.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;
import com.shopwise.customer.PasswordResetRequested;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class AccountIntegrationTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AccountService accounts;

    @Autowired
    ApplicationEvents events;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private Cookie register(String email, String password) {
        var result = mvc.post().uri("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "name": "Pat Doe"}
                        """.formatted(email, password))
                .exchange();
        assertThat(result).hasStatus(201);
        var cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("session cookie").isNotNull();
        return cookie;
    }

    private String addressJson(String line1) {
        return """
                {"recipient": "Pat Doe", "line1": "%s", "city": "Springfield", "state": "IL", "postalCode": "62701"}
                """.formatted(line1);
    }

    @Test
    void registeringSignsTheCustomerIn() {
        var session = register(uniqueEmail(), "correct horse");

        assertThat(mvc.get().uri("/api/auth/session").cookie(session))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.authenticated")
                .isEqualTo(true);
    }

    @Test
    void anEmailCanOnlyBeRegisteredOnce() {
        var email = uniqueEmail();
        register(email, "correct horse");

        assertThat(mvc.post().uri("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "another one", "name": "Pat"}
                        """.formatted(email.toUpperCase())))
                .hasStatus(409);
    }

    @Test
    void shortPasswordsAreRejected() {
        assertThatThrownBy(() -> accounts.register(uniqueEmail(), "short", "Pat"))
                .isInstanceOf(AccountException.class)
                .hasMessageContaining("at least 8");
    }

    @Test
    void loginWithTheRightPasswordWorksAndTheWrongOneDoesNot() {
        var email = uniqueEmail();
        register(email, "correct horse");

        assertThat(mvc.post().uri("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"wrong pass\"}".formatted(email)))
                .hasStatus(401);
        assertThat(mvc.post().uri("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"correct horse\"}".formatted(email)))
                .hasStatusOk();
    }

    @Test
    void repeatedFailuresBlockSignInForThatEmail() {
        var email = uniqueEmail();
        register(email, "correct horse");
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            mvc.post().uri("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"%s\", \"password\": \"nope nope\"}".formatted(email)).exchange();
        }

        assertThat(mvc.post().uri("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"correct horse\"}".formatted(email)))
                .hasStatus(429);
    }

    @Test
    void accountEndpointsNeedASignedInCustomer() {
        assertThat(mvc.get().uri("/api/me")).hasStatus(401);
    }

    @Test
    void stateChangingRequestsNeedACsrfToken() {
        assertThat(mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"a@example.com\", \"password\": \"whatever1\"}"))
                .hasStatus(403);
    }

    @Test
    void firstAddressIsTheDefaultAndDefaultsMove() throws Exception {
        var session = register(uniqueEmail(), "correct horse");

        var first = mvc.post().uri("/api/me/addresses").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(addressJson("1 Main St")).exchange();
        assertThat(first).hasStatus(201).bodyJson().extractingPath("$.isDefault").isEqualTo(true);
        var second = mvc.post().uri("/api/me/addresses").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(addressJson("2 Oak Ave")).exchange();
        assertThat(second).hasStatus(201).bodyJson().extractingPath("$.isDefault").isEqualTo(false);

        var secondId = second.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        assertThat(mvc.post().uri("/api/me/addresses/" + secondId + "/default").cookie(session).with(csrf()))
                .hasStatusOk();

        assertThat(mvc.get().uri("/api/me/addresses").cookie(session))
                .bodyJson()
                .extractingPath("$[?(@.isDefault == true)].line1")
                .asArray()
                .containsExactly("2 Oak Ave");
    }

    @Test
    void customersCannotTouchOtherCustomersAddresses() throws Exception {
        var owner = register(uniqueEmail(), "correct horse");
        var created = mvc.post().uri("/api/me/addresses").cookie(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(addressJson("1 Main St")).exchange();
        var id = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        var intruder = register(uniqueEmail(), "correct horse");

        assertThat(mvc.delete().uri("/api/me/addresses/" + id).cookie(intruder).with(csrf())).hasStatus(404);
    }

    @Test
    void passwordResetLinkWorksOnce() {
        var email = uniqueEmail();
        register(email, "correct horse");

        assertThat(mvc.post().uri("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email))).hasStatus(202);
        var link = events.stream(PasswordResetRequested.class).reduce((a, b) -> b).orElseThrow().resetLink();
        var token = link.substring(link.indexOf("token=") + 6);

        accounts.resetPassword(token, "brand new password");

        assertThat(mvc.post().uri("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"brand new password\"}".formatted(email)))
                .hasStatusOk();
        assertThatThrownBy(() -> accounts.resetPassword(token, "yet another one"))
                .isInstanceOf(AccountException.class);
    }

    @Test
    void passwordResetForAnUnknownEmailLooksTheSame() {
        assertThat(mvc.post().uri("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nobody-%s@example.com\"}".formatted(UUID.randomUUID()))).hasStatus(202);
    }

    @Test
    void googleSignInLinksVerifiedEmailsAndRejectsUnverifiedOnes() {
        var email = uniqueEmail();
        var registered = accounts.register(email, "correct horse", "Pat");

        var linked = accounts.signInWithGoogle("google-" + UUID.randomUUID(), email, true, "Pat G");
        assertThat(linked.getId()).isEqualTo(registered.getId());

        var created = accounts.signInWithGoogle("google-" + UUID.randomUUID(), uniqueEmail(), true, "New Person");
        assertThat(created.getPasswordHash()).isNull();

        assertThatThrownBy(() -> accounts.signInWithGoogle("google-" + UUID.randomUUID(), uniqueEmail(), false, "X"))
                .isInstanceOf(AccountException.class);
    }
}
