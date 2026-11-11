package com.shopwise.catalog.internal;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.catalog.OutOfStockException;
import com.shopwise.catalog.ProductUpdated;
import com.shopwise.catalog.StockChange;
import com.shopwise.catalog.StockService;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class JdbcStockService implements StockService {

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    JdbcStockService(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Override
    public void reserve(List<StockChange> changes) {
        // Lock rows in a stable order so concurrent orders can't deadlock.
        var sorted = changes.stream().sorted((a, b) -> Long.compare(a.variantId(), b.variantId())).toList();
        for (var change : sorted) {
            int updated = jdbc.sql("update inventory set quantity = quantity - :q where variant_id = :id and quantity >= :q")
                    .param("q", change.quantity())
                    .param("id", change.variantId())
                    .update();
            if (updated == 0) {
                throw new OutOfStockException(change.variantId());
            }
        }
        announce(changes);
    }

    @Override
    public void release(List<StockChange> changes) {
        for (var change : changes) {
            jdbc.sql("update inventory set quantity = quantity + :q where variant_id = :id")
                    .param("q", change.quantity())
                    .param("id", change.variantId())
                    .update();
        }
        announce(changes);
    }

    private void announce(List<StockChange> changes) {
        Set<Long> productIds = new TreeSet<>(jdbc.sql("select distinct product_id from product_variant where id in (:ids)")
                .param("ids", changes.stream().map(StockChange::variantId).toList())
                .query(Long.class)
                .list());
        productIds.forEach(id -> events.publishEvent(new ProductUpdated(id)));
    }
}
