package com.shopwise.backoffice.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shopwise.order.AdminOrderRow;
import com.shopwise.order.AdminOrderView;
import com.shopwise.order.CheckoutException;
import com.shopwise.order.OrderAdmin;
import com.shopwise.order.OrderStatus;

import jakarta.validation.constraints.Size;

/**
 * Admin-only (secured under /api/admin). Thin layer over the order module's admin API.
 */
@RestController
@RequestMapping("/api/admin/orders")
class OrderAdminController {

    private final OrderAdmin orders;

    OrderAdminController(OrderAdmin orders) {
        this.orders = orders;
    }

    @GetMapping
    List<AdminOrderRow> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q) {
        return orders.list(status, q);
    }

    @GetMapping("/{id}")
    AdminOrderView get(@PathVariable long id) {
        return orders.get(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
    }

    @PostMapping("/{id}/ship")
    AdminOrderView ship(@PathVariable long id, @RequestBody(required = false) Shipment body) {
        return orders.markShipped(id, body == null ? null : body.trackingNumber());
    }

    @PostMapping("/{id}/deliver")
    AdminOrderView deliver(@PathVariable long id) {
        return orders.markDelivered(id);
    }

    @PostMapping("/{id}/cancel")
    AdminOrderView cancel(@PathVariable long id) {
        return orders.cancel(id);
    }

    @ExceptionHandler
    ProblemDetail handle(CheckoutException e) {
        var status = e.reason() == CheckoutException.Reason.NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }

    record Shipment(@Size(max = 100) String trackingNumber) {
    }
}
