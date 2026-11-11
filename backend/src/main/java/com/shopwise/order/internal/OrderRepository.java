package com.shopwise.order.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerIdOrderByPlacedAtDesc(long customerId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdAndCustomerId(long id, long customerId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByCustomerIdAndIdempotencyKey(long customerId, String idempotencyKey);
}
