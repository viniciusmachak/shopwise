package com.shopwise.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.shopwise.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogIntegrationTests {

    @Autowired
    CatalogService catalog;

    @Autowired
    MockMvcTester mvc;

    @Test
    void filtersByCategoryAndPrice() {
        var filter = new ProductFilter("running-shoes", null, new BigDecimal("100"), null);

        var page = catalog.findProducts(filter, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .isNotEmpty()
                .allSatisfy(p -> {
                    assertThat(p.category()).isEqualTo("Running Shoes");
                    assertThat(p.price().amount()).isLessThanOrEqualTo(new BigDecimal("100"));
                });
    }

    @Test
    void productDetailIncludesVariantsWithStock() {
        var id = catalog.findProducts(new ProductFilter("running-shoes", null, null, null), PageRequest.of(0, 1))
                .getContent().getFirst().id();

        var detail = catalog.findProduct(id).orElseThrow();

        assertThat(detail.variants()).hasSize(5);
        assertThat(detail.variants()).allSatisfy(v -> assertThat(v.price()).isEqualTo(detail.price()));
    }

    @Test
    void summariesKeepTheRequestedOrder() {
        var ids = catalog.allProductIds();
        var reversed = List.of(ids.get(2), ids.get(0), ids.get(1));

        assertThat(catalog.findSummaries(reversed)).extracting(ProductSummary::id).containsExactlyElementsOf(reversed);
    }

    @Test
    void unknownProductIsNotFound() {
        assertThat(mvc.get().uri("/api/products/999999")).hasStatus(404);
    }

    @Test
    void listsProductsOverHttp() {
        assertThat(mvc.get().uri("/api/products?category=backpacks"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.content.length()")
                .isEqualTo(4);
    }
}
