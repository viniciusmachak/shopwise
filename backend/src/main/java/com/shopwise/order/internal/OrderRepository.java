package com.shopwise.order.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.shopwise.order.OrderStatus;

interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerIdOrderByPlacedAtDesc(long customerId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdAndCustomerId(long id, long customerId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByCustomerIdAndIdempotencyKey(long customerId, String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    @Query("""
            select o from Order o
            where o.status <> com.shopwise.order.OrderStatus.PENDING_PAYMENT
              and (:status is null or o.status = :status)
            order by o.placedAt desc
            """)
    List<Order> findForBackoffice(OrderStatus status, Pageable page);
}
