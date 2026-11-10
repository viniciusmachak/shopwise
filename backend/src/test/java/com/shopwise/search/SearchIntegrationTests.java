package com.shopwise.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shopwise.TestcontainersConfiguration;
import com.shopwise.catalog.ProductSummary;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SearchIntegrationTests {

    @Autowired
    SearchService search;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void waitForIndex() {
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> jdbc
                .sql("select count(*) from search_document where embedding is not null")
                .query(Integer.class).single() == 19);
    }

    @Test
    void findsByExactName() {
        assertThat(search.search(SearchQuery.of("Trail Runner X")))
                .first()
                .extracting(ProductSummary::name)
                .isEqualTo("Trail Runner X");
    }

    @Test
    void findsByMeaningWhenWordsDoNotAllMatch() {
        var results = search.search(SearchQuery.of("something waterproof for running in the rain"));

        assertThat(results).extracting(ProductSummary::name).contains("Stormguard GTX Runner");
    }

    @Test
    void appliesStructuredFilters() {
        var query = new SearchQuery("shoe", "running-shoes", null, new BigDecimal("100"), null, 10);

        assertThat(search.search(query))
                .isNotEmpty()
                .allSatisfy(p -> {
                    assertThat(p.category()).isEqualTo("Running Shoes");
                    assertThat(p.price().amount()).isLessThanOrEqualTo(new BigDecimal("100"));
                });
    }

    @Test
    void blankQueryReturnsNothing() {
        assertThat(search.search(SearchQuery.of("  "))).isEmpty();
    }
}
