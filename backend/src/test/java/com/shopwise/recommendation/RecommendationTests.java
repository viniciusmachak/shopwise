package com.shopwise.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.TestcontainersConfiguration;
import com.shopwise.catalog.ProductSummary;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecommendationTests {

    @Autowired
    RecommendationService recommendations;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @BeforeEach
    void waitForEmbeddings() {
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> jdbc
                .sql("select count(*) from search_document where embedding is null").query(Integer.class).single() == 0
                && jdbc.sql("select count(*) from search_document").query(Integer.class).single() > 0);
    }

    private long productId(String slug) {
        return jdbc.sql("select id from product where slug = :s").param("s", slug).query(Long.class).single();
    }

    /** A paid order holding one unit of each product's first variant, inserted directly. */
    private void paidOrderWith(String... slugs) {
        long customer = jdbc.sql("insert into customer (email, name) values (:e, 'R') returning id")
                .param("e", "rec-" + UUID.randomUUID() + "@example.test").query(Long.class).single();
        long order = jdbc.sql("""
                insert into orders (customer_id, status, subtotal, discount, shipping, total, currency, shipping_option,
                    ship_recipient, ship_line1, ship_city, ship_state, ship_postal_code, ship_country, idempotency_key)
                values (:c, 'PAID', 1, 0, 0, 1, 'USD', 'STANDARD', 'R', '1 Main St', 'X', 'IL', '1', 'US', :k)
                returning id
                """).param("c", customer).param("k", UUID.randomUUID().toString()).query(Long.class).single();
        for (var slug : slugs) {
            jdbc.sql("""
                    insert into order_item (order_id, variant_id, product_id, product_name, unit_price, quantity, line_total)
                    select :o, v.id, p.id, p.name, p.price, 1, p.price
                    from product p join product_variant v on v.product_id = p.id
                    where p.slug = :s order by v.id limit 1
                    """).param("o", order).param("s", slug).update();
        }
    }

    @Test
    void similarProductsComeFromTheSameCategoryAndExcludeTheProduct() {
        var result = recommendations.forProduct(productId("stormguard-gtx-runner"), 4).orElseThrow();

        assertThat(result.similar()).isNotEmpty()
                .allSatisfy(p -> assertThat(p.category()).isEqualTo("Running Shoes"))
                .extracting(ProductSummary::slug).doesNotContain("stormguard-gtx-runner");
    }

    @Test
    void productsBoughtTogetherComeFirst() {
        paidOrderWith("summit-pro", "merino-tee");
        paidOrderWith("summit-pro", "merino-tee", "daypack-22");

        var result = recommendations.forProduct(productId("summit-pro"), 4).orElseThrow();

        assertThat(result.goesWith()).first().extracting(ProductSummary::slug).isEqualTo("merino-tee");
        assertThat(result.goesWith()).extracting(ProductSummary::slug).contains("daypack-22");
    }

    @Test
    void withoutOrderHistoryComplementaryCategoriesFillIn() {
        var result = recommendations.forProduct(productId("windrunner"), 3).orElseThrow();

        assertThat(result.goesWith()).hasSize(3)
                .allSatisfy(p -> assertThat(p.category()).isIn("T-Shirts", "Backpacks", "Hiking Boots"))
                .allSatisfy(p -> assertThat(p.inStock()).isTrue());
    }

    @Test
    void theStoreShowsRecommendationsOnProductPages() {
        assertThat(mvc.get().uri("/api/products/" + productId("trail-runner-x") + "/recommendations"))
                .hasStatusOk().bodyJson().extractingPath("$.goesWith.length()").isNotEqualTo(0);
        assertThat(mvc.get().uri("/api/products/999999/recommendations")).hasStatus(404);
    }
}
