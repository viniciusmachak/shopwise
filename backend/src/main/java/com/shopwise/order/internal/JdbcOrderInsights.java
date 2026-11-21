package com.shopwise.order.internal;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.shopwise.order.OrderInsights;

@Component
class JdbcOrderInsights implements OrderInsights {

    private final JdbcClient jdbc;

    JdbcOrderInsights(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Long> boughtTogether(long productId, int limit) {
        return jdbc.sql("""
                select other.product_id
                from order_item mine
                join orders o on o.id = mine.order_id and o.status in ('PAID', 'SHIPPED', 'DELIVERED')
                join order_item other on other.order_id = mine.order_id and other.product_id <> mine.product_id
                where mine.product_id = :id
                group by other.product_id
                order by count(distinct mine.order_id) desc, other.product_id
                limit :limit
                """)
                .param("id", productId)
                .param("limit", Math.clamp(limit, 1, 20))
                .query(Long.class)
                .list();
    }
}
