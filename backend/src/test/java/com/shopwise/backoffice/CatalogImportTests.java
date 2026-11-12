package com.shopwise.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jayway.jsonpath.JsonPath;
import com.shopwise.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "shopwise.admin.emails=catalog@example.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogImportTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    Cookie admin;

    @BeforeEach
    void signIn() {
        jdbc.sql("delete from customer where email = 'catalog@example.test'").update();
        admin = mvc.post().uri("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"catalog@example.test\", \"password\": \"correct horse\", \"name\": \"Cat\"}")
                .exchange().getResponse().getCookie("SESSION");
    }

    private String upload(String csv) throws Exception {
        var file = new MockMultipartFile("file", "catalog.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var result = mvc.post().uri("/api/admin/imports").multipart().file(file).cookie(admin).with(csrf()).exchange();
        assertThat(result).hasStatusOk();
        return result.getResponse().getContentAsString();
    }

    private int stockOf(String sku) {
        return jdbc.sql("select i.quantity from inventory i join product_variant v on v.id = i.variant_id where v.sku = :sku")
                .param("sku", sku).query(Integer.class).single();
    }

    @Test
    void importsGoodRowsAndReportsBadOnesByLine() throws Exception {
        var tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        var existingSku = jdbc.sql("select sku from product_variant order by id limit 1").query(String.class).single();
        var csv = String.join("\n",
                "sku,product_name,brand,category,description,price,size,color,stock",
                "BEAN-" + tag + "-S,Beanie " + tag + ",Alpstep,t-shirts,\"Warm, soft beanie.\",24.00,S,Red,10",
                "BEAN-" + tag + "-M,Beanie " + tag + ",Alpstep,t-shirts,,24.00,M,Red,6",
                existingSku + ",Trail Runner X,Northpeak,running-shoes,,89.90,8,Black,42",
                "BAD-" + tag + "-1,No Price,Alpstep,t-shirts,,,M,Red,1",
                "BAD-" + tag + "-2,Bad Stock,Alpstep,t-shirts,,10,M,Red,lots",
                "BAD-" + tag + "-3,Spaceship,Alpstep,spaceships,,10,M,Red,1",
                "");

        var report = upload(csv);

        assertThat((String) JsonPath.read(report, "$.status")).isEqualTo("COMPLETED");
        assertThat((Integer) JsonPath.read(report, "$.read")).isEqualTo(6);
        assertThat((Integer) JsonPath.read(report, "$.skipped")).isEqualTo(3);
        List<Integer> lines = JsonPath.read(report, "$.errors[*].line");
        assertThat(lines).containsExactlyInAnyOrder(5, 6, 7);
        List<String> line5 = JsonPath.read(report, "$.errors[?(@.line == 5)].message");
        assertThat(line5).singleElement().asString().contains("price");
        assertThat(report).contains("spaceships");

        assertThat(jdbc.sql("select count(*) from product_variant v join product p on p.id = v.product_id where p.name = :n")
                .param("n", "Beanie " + tag).query(Integer.class).single()).isEqualTo(2);
        assertThat(stockOf(existingSku)).isEqualTo(42);
        assertThat(jdbc.sql("select description from product where name = :n").param("n", "Beanie " + tag)
                .query(String.class).single()).isEqualTo("Warm, soft beanie.");
    }

    @Test
    void aSkuCannotMoveToAnotherProduct() throws Exception {
        var existingSku = jdbc.sql("select sku from product_variant order by id limit 1").query(String.class).single();

        var report = upload("sku,product_name,brand,category,description,price,size,color,stock\n"
                + existingSku + ",Totally Different Product,Velo,jackets,,10,M,Red,1\n");

        assertThat((Integer) JsonPath.read(report, "$.skipped")).isEqualTo(1);
        assertThat(report).contains("already belongs to another product");
        assertThat((Integer) JsonPath.read(report, "$.errors[0].line")).isEqualTo(2);
    }

    @Test
    void keepsAnImportHistoryAndOffersATemplate() throws Exception {
        upload("sku,product_name,brand,category,description,price,size,color,stock\n");

        assertThat(mvc.get().uri("/api/admin/imports").cookie(admin)).hasStatusOk()
                .bodyJson().extractingPath("$[0].fileName").isEqualTo("catalog.csv");
        assertThat(mvc.get().uri("/api/admin/imports/template").cookie(admin)).hasStatusOk()
                .bodyText().startsWith("sku,product_name,brand,category");
    }

    @Test
    void customersCannotImport() {
        var customer = mvc.post().uri("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"c-%s@example.test\", \"password\": \"correct horse\", \"name\": \"C\"}"
                        .formatted(UUID.randomUUID()))
                .exchange().getResponse().getCookie("SESSION");

        assertThat(mvc.get().uri("/api/admin/imports").cookie(customer)).hasStatus(403);
    }
}
