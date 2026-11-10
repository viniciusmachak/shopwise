package com.shopwise.cart.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopwise.cart.CartException;
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.cart.CartView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/cart")
class CartController {

    private final CartService carts;

    CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    CartView view(CartKey key) {
        return carts.view(key);
    }

    @PostMapping("/items")
    CartView add(CartKey key, @Valid @RequestBody AddItem body) {
        return carts.addItem(key, body.variantId(), body.quantity());
    }

    @PatchMapping("/items/{itemId}")
    CartView update(CartKey key, @PathVariable long itemId, @Valid @RequestBody UpdateItem body) {
        return carts.updateItem(key, itemId, body.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    CartView remove(CartKey key, @PathVariable long itemId) {
        return carts.updateItem(key, itemId, 0);
    }

    @PostMapping("/coupon")
    CartView applyCoupon(CartKey key, @Valid @RequestBody ApplyCoupon body) {
        return carts.applyCoupon(key, body.code());
    }

    @DeleteMapping("/coupon")
    CartView removeCoupon(CartKey key) {
        return carts.removeCoupon(key);
    }

    @ExceptionHandler
    ProblemDetail handle(CartException e) {
        var status = switch (e.reason()) {
            case VARIANT_NOT_FOUND, ITEM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case OUT_OF_STOCK -> HttpStatus.CONFLICT;
            case INVALID_QUANTITY, INVALID_COUPON -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("reason", e.reason().name());
        return problem;
    }

    record AddItem(@NotNull Long variantId, @Min(1) int quantity) {
    }

    record UpdateItem(@Min(0) int quantity) {
    }

    record ApplyCoupon(@NotBlank String code) {
    }
}
