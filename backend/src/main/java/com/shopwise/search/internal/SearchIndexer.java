package com.shopwise.search.internal;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopwise.catalog.CatalogService;
import com.shopwise.catalog.IndexableProduct;
import com.shopwise.catalog.ProductUpdated;

/**
 * Keeps search_document in sync with the catalog. Text is indexed right away;
 * embeddings are added when the embedding model is reachable.
 */
@Component
class SearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);

    private final CatalogService catalog;
    private final Embeddings embeddings;
    private final JdbcClient jdbc;

    SearchIndexer(CatalogService catalog, Embeddings embeddings, JdbcClient jdbc) {
        this.catalog = catalog;
        this.embeddings = embeddings;
        this.jdbc = jdbc;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    void indexAllOnStartup() {
        var ids = catalog.allProductIds();
        ids.forEach(this::indexText);
        log.info("Indexed text for {} products", ids.size());
        embedMissing();
    }

    @ApplicationModuleListener
    void on(ProductUpdated event) {
        indexText(event.productId());
        embedMissing();
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    void embedMissing() {
        List<Row> missing = jdbc.sql("select product_id, document from search_document where embedding is null")
                .query(Row.class)
                .list();
        int done = 0;
        for (var row : missing) {
            try {
                var vector = embeddings.document(row.document());
                jdbc.sql("update search_document set embedding = cast(:embedding as vector) where product_id = :id")
                        .param("embedding", Embeddings.toLiteral(vector))
                        .param("id", row.productId())
                        .update();
                done++;
            }
            catch (RuntimeException e) {
                log.warn("Could not embed products ({} left): {}", missing.size() - done, e.getMessage());
                return;
            }
        }
        if (done > 0) {
            log.info("Embedded {} products", done);
        }
    }

    void indexText(long productId) {
        catalog.findIndexable(productId).ifPresentOrElse(this::upsert,
                () -> jdbc.sql("delete from search_document where product_id = :id").param("id", productId).update());
    }

    private void upsert(IndexableProduct product) {
        jdbc.sql("""
                insert into search_document (product_id, category, price, in_stock, document, embedding, indexed_at)
                values (:id, :category, :price, :inStock, :document, null, now())
                on conflict (product_id) do update set
                    category = excluded.category,
                    price = excluded.price,
                    in_stock = excluded.in_stock,
                    embedding = case when search_document.document = excluded.document
                                     then search_document.embedding end,
                    document = excluded.document,
                    indexed_at = now()
                """)
                .param("id", product.id())
                .param("category", product.category())
                .param("price", product.price())
                .param("inStock", product.inStock())
                .param("document", product.document())
                .update();
    }

    record Row(long productId, String document) {
    }
}
