package com.shopwise.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Duration;
import java.util.UUID;

import org.awaitility.Awaitility;
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
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.search.SearchQuery;
import com.shopwise.search.SearchService;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "shopwise.admin.emails=Boss@Example.test, other@example.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductAdminTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SearchService search;

    @Autowired
    CartService carts;

    private Cookie signUp(String email) {
        var result = mvc.post().uri("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "correct horse", "name": "Pat"}
                        """.formatted(email))
                .exchange();
        assertThat(result).hasStatus(201);
        return result.getResponse().getCookie("SESSION");
    }

    private Cookie admin() {
        jdbc.sql("delete from customer where lower(email) = 'boss@example.test'").update();
        return signUp("boss@example.test");
    }

    private static final String PRODUCT = """
            {"name": "Glacier Parka %s", "brand": "Alpstep", "description": "Very warm parka for deep winter.",
             "category": "jackets", "price": 249.00, "attributes": {"insulated": true}}
            """;

    private long createProduct(Cookie admin, String suffix) throws Exception {
        var created = mvc.post().uri("/api/admin/products").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(PRODUCT.formatted(suffix)).exchange();
        assertThat(created).hasStatus(201);
        return ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long addVariant(Cookie admin, long productId, String sku, int stock) throws Exception {
        var result = mvc.post().uri("/api/admin/products/" + productId + "/variants").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variant\": {\"sku\": \"%s\", \"size\": \"M\"}, \"stock\": %d}".formatted(sku, stock))
                .exchange();
        assertThat(result).hasStatus(201);
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.variants[-1].id")).longValue();
    }

    @Test
    void onlyAdminsCanUseTheBackoffice() {
        assertThat(mvc.get().uri("/api/admin/products")).hasStatus(401);
        var customer = signUp("customer-" + UUID.randomUUID() + "@example.test");
        assertThat(mvc.get().uri("/api/admin/products").cookie(customer)).hasStatus(403);
        assertThat(mvc.get().uri("/api/admin/products").cookie(admin())).hasStatusOk();
    }

    @Test
    void configuredEmailsBecomeAdminsWhateverTheirCase() {
        var session = admin();

        assertThat(mvc.get().uri("/api/auth/session").cookie(session))
                .bodyJson().extractingPath("$.customer.admin").isEqualTo(true);
    }

    @Test
    void aNewProductShowsUpInTheStoreAndSearch() throws Exception {
        var admin = admin();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = createProduct(admin, suffix);
        addVariant(admin, id, "PARKA-" + suffix, 5);

        assertThat(mvc.get().uri("/api/products/" + id)).hasStatusOk()
                .bodyJson().extractingPath("$.slug").isEqualTo("glacier-parka-" + suffix);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                search.search(SearchQuery.of("Glacier Parka " + suffix)).stream().anyMatch(p -> p.id() == id));
    }

    @Test
    void archivingHidesTheProductAndStopsSales() throws Exception {
        var admin = admin();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = createProduct(admin, suffix);
        long variant = addVariant(admin, id, "ARCH-" + suffix, 5);
        var cart = CartKey.random();
        carts.addItem(cart, variant, 1);

        assertThat(mvc.post().uri("/api/admin/products/" + id + "/archive").cookie(admin).with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$.active").isEqualTo(false);

        assertThat(mvc.get().uri("/api/products/" + id)).hasStatus(404);
        assertThat(carts.view(cart).items()).singleElement().satisfies(l -> assertThat(l.available()).isFalse());
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> jdbc
                .sql("select count(*) from search_document where product_id = :id").param("id", id)
                .query(Integer.class).single() == 0);

        assertThat(mvc.post().uri("/api/admin/products/" + id + "/restore").cookie(admin).with(csrf())).hasStatusOk();
        assertThat(mvc.get().uri("/api/products/" + id)).hasStatusOk();
    }

    @Test
    void stockAndVariantsCanBeChanged() throws Exception {
        var admin = admin();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = createProduct(admin, suffix);
        long variant = addVariant(admin, id, "STOCK-" + suffix, 2);

        assertThat(mvc.put().uri("/api/admin/products/variants/" + variant + "/stock").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 9}"))
                .hasStatusOk().bodyJson().extractingPath("$.variants[0].stock").isEqualTo(9);
        assertThat(mvc.put().uri("/api/admin/products/variants/" + variant + "/stock").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": -1}"))
                .hasStatus(400);
        assertThat(mvc.post().uri("/api/admin/products/" + id + "/variants").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variant\": {\"sku\": \"stock-%s\"}, \"stock\": 1}".formatted(suffix)))
                .hasStatus(409);
    }

    @Test
    void unknownCategoriesAreRejected() {
        assertThat(mvc.post().uri("/api/admin/products").cookie(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "X", "brand": "Y", "description": "Z", "category": "spaceships", "price": 1}
                        """))
                .hasStatus(422);
    }
}
