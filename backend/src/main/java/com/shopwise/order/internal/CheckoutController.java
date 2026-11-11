package com.shopwise.order.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shopwise.cart.CartKey;
import com.shopwise.customer.CurrentCustomer;
import com.shopwise.order.CheckoutException;
import com.shopwise.order.CheckoutQuote;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderService;
import com.shopwise.order.OrderSummary;
import com.shopwise.order.OrderView;
import com.shopwise.order.PlaceOrderRequest;
import com.shopwise.order.ShippingOption;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
class CheckoutController {

    private final CheckoutService checkout;
    private final OrderService orders;

    CheckoutController(CheckoutService checkout, OrderService orders) {
        this.checkout = checkout;
        this.orders = orders;
    }

    @PostMapping("/api/checkout/quote")
    CheckoutQuote quote(CartKey cartKey, @RequestBody(required = false) QuoteForm form) {
        var f = form == null ? new QuoteForm(null, null) : form;
        return checkout.quote(CurrentCustomer.require().id(), cartKey, f.addressId(), f.shipping());
    }

    @PostMapping("/api/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    OrderView placeOrder(CartKey cartKey, @Valid @RequestBody PlaceOrderForm form) {
        return checkout.placeOrder(CurrentCustomer.require().id(), cartKey,
                new PlaceOrderRequest(form.addressId(), form.shipping(), form.cartHash(), form.idempotencyKey()));
    }

    @GetMapping("/api/orders")
    List<OrderSummary> orders() {
        return orders.list(CurrentCustomer.require().id());
    }

    @GetMapping("/api/orders/{id}")
    OrderView order(@PathVariable long id) {
        return orders.find(CurrentCustomer.require().id(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
    }

    @PostMapping("/api/orders/{id}/cancel")
    OrderView cancel(@PathVariable long id) {
        return orders.cancel(CurrentCustomer.require().id(), id);
    }

    @ExceptionHandler
    ProblemDetail handle(CheckoutException e) {
        var status = switch (e.reason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CART_CHANGED, OUT_OF_STOCK, ITEM_UNAVAILABLE, NOT_CANCELLABLE -> HttpStatus.CONFLICT;
            case PAYMENT_DECLINED -> HttpStatus.PAYMENT_REQUIRED;
            case EMPTY_CART, NO_ADDRESS -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("reason", e.reason().name());
        return problem;
    }

    record QuoteForm(Long addressId, ShippingOption shipping) {
    }

    record PlaceOrderForm(@NotNull Long addressId, @NotNull ShippingOption shipping, @NotBlank String cartHash,
            @NotBlank @Size(max = 100) String idempotencyKey) {
    }
}
