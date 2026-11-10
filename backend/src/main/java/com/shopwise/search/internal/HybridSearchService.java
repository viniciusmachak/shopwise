package com.shopwise.search.internal;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.shopwise.catalog.CatalogService;
import com.shopwise.catalog.ProductSummary;
import com.shopwise.search.SearchQuery;
import com.shopwise.search.SearchService;

/**
 * Full-text and vector rankings merged with reciprocal rank fusion (k = 60).
 */
@Service
class HybridSearchService implements SearchService {

    private static final String FILTERED = """
            with filtered as (
                select * from search_document
                where (cast(:category as text) is null or category = :category)
                  and (cast(:minPrice as numeric) is null or price >= :minPrice)
                  and (cast(:maxPrice as numeric) is null or price <= :maxPrice)
                  and (cast(:inStock as boolean) is null or not :inStock or in_stock)
            ),
            text_rank as (
                select product_id, row_number() over (order by ts_rank_cd(tsv, q) desc) as r
                from filtered, websearch_to_tsquery('english', :text) q
                where tsv @@ q
                limit 50
            )
            """;

    private static final String VECTOR_RANK = """
            , vector_rank as (
                select product_id, row_number() over (order by embedding <=> cast(:embedding as vector)) as r
                from filtered
                where embedding is not null
                order by embedding <=> cast(:embedding as vector)
                limit 20
            )
            """;

    private static final String FUSE = """
            select product_id from (%s) ranks
            group by product_id
            order by sum(1.0 / (60 + r)) desc
            limit :limit
            """;

    private final JdbcClient jdbc;
    private final Embeddings embeddings;
    private final CatalogService catalog;

    HybridSearchService(JdbcClient jdbc, Embeddings embeddings, CatalogService catalog) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.catalog = catalog;
    }

    @Override
    public List<ProductSummary> search(SearchQuery query) {
        if (query.text() == null || query.text().isBlank()) {
            return List.of();
        }
        var embedding = embeddings.query(query.text());
        var sql = embedding.isPresent()
                ? FILTERED + VECTOR_RANK + FUSE.formatted("select * from text_rank union all select * from vector_rank")
                : FILTERED + FUSE.formatted("select * from text_rank");

        var statement = jdbc.sql(sql)
                .param("text", query.text())
                .param("category", query.category())
                .param("minPrice", query.minPrice())
                .param("maxPrice", query.maxPrice())
                .param("inStock", query.inStock())
                .param("limit", query.limit());
        embedding.ifPresent(e -> statement.param("embedding", Embeddings.toLiteral(e)));

        List<Long> ids = statement.query(Long.class).list();
        return catalog.findSummaries(ids);
    }
}
