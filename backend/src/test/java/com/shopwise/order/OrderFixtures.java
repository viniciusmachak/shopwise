package com.shopwise.order;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Creates customers and addresses directly in the database for order tests.
 */
record OrderFixtures(JdbcClient jdbc) {

    long customer() {
        return jdbc.sql("insert into customer (email, name) values (:email, 'Pat Doe') returning id")
                .param("email", "order-" + UUID.randomUUID() + "@example.com")
                .query(Long.class).single();
    }

    long address(long customerId, boolean isDefault) {
        return jdbc.sql("""
                insert into address (customer_id, recipient, line1, city, state, postal_code, is_default)
                values (:customer, 'Pat Doe', '123 Main St', 'Springfield', 'IL', '62701', :isDefault)
                returning id
                """).param("customer", customerId).param("isDefault", isDefault).query(Long.class).single();
    }

    /** A variant with at least this much stock, skipping the first {@code skip} matches. */
    long variant(int minStock, int skip) {
        return jdbc.sql("select variant_id from inventory where quantity >= :q order by variant_id desc offset :skip limit 1")
                .param("q", minStock).param("skip", skip).query(Long.class).single();
    }

    int stock(long variantId) {
        return jdbc.sql("select quantity from inventory where variant_id = :id").param("id", variantId)
                .query(Integer.class).single();
    }

    long variantPricedAtLeast(String price, int minStock) {
        return jdbc.sql("""
                select v.id from product_variant v join product p on p.id = v.product_id
                join inventory i on i.variant_id = v.id
                where p.price >= cast(:price as numeric) and i.quantity >= :q order by v.id desc limit 1
                """).param("price", price).param("q", minStock).query(Long.class).single();
    }

    long variantPricedBelow(String price, int minStock) {
        return jdbc.sql("""
                select v.id from product_variant v join product p on p.id = v.product_id
                join inventory i on i.variant_id = v.id
                where p.price < cast(:price as numeric) and i.quantity >= :q order by v.id desc limit 1
                """).param("price", price).param("q", minStock).query(Long.class).single();
    }
}
