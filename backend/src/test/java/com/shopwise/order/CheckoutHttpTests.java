package com.shopwise.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jayway.jsonpath.JsonPath;
import com.shopwise.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckoutHttpTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aCustomerChecksOutThroughTheApi() throws Exception {
        var registered = mvc.post().uri("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "http-%s@example.com", "password": "correct horse", "name": "Pat"}
                        """.formatted(UUID.randomUUID()))
                .exchange();
        Cookie session = registered.getResponse().getCookie("SESSION");

        var address = mvc.post().uri("/api/me/addresses").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipient": "Pat", "line1": "1 Main St", "city": "Springfield", "state": "IL", "postalCode": "62701"}
                        """).exchange();
        int addressId = JsonPath.read(address.getResponse().getContentAsString(), "$.id");

        long variant = new OrderFixtures(jdbc).variant(2, 7);
        var added = mvc.post().uri("/api/cart/items").cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\": %d, \"quantity\": 1}".formatted(variant)).exchange();
        Cookie cart = added.getResponse().getCookie("shopwise_cart");

        var quote = mvc.post().uri("/api/checkout/quote").cookie(session, cart).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();
        assertThat(quote).hasStatusOk();
        String hash = JsonPath.read(quote.getResponse().getContentAsString(), "$.cartHash");

        assertThat(mvc.post().uri("/api/checkout").cookie(session, cart).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"addressId": %d, "shipping": "STANDARD", "cartHash": "%s", "idempotencyKey": "%s"}
                        """.formatted(addressId, hash, UUID.randomUUID())))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PAID");

        assertThat(mvc.get().uri("/api/orders").cookie(session))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.length()")
                .isEqualTo(1);
    }
}
