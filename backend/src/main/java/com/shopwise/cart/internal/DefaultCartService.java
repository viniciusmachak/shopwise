package com.shopwise.cart.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.cart.CartException;
import com.shopwise.cart.CartException.Reason;
import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.cart.CartView;
import com.shopwise.catalog.CatalogService;
import com.shopwise.catalog.Money;
import com.shopwise.catalog.VariantView;

@Service
@Transactional
class DefaultCartService implements CartService {

    private final CartRepository carts;
    private final CouponRepository coupons;
    private final CatalogService catalog;

    DefaultCartService(CartRepository carts, CouponRepository coupons, CatalogService catalog) {
        this.carts = carts;
        this.coupons = coupons;
        this.catalog = catalog;
    }

    @Override
    @Transactional(readOnly = true)
    public CartView view(CartKey key) {
        return carts.findByCartKey(key.value()).map(this::toView).orElseGet(DefaultCartService::empty);
    }

    @Override
    public CartView addItem(CartKey key, long variantId, int quantity) {
        if (quantity < 1) {
            throw new CartException(Reason.INVALID_QUANTITY, "Quantity must be at least 1.");
        }
        var variant = catalog.findVariant(variantId)
                .orElseThrow(() -> new CartException(Reason.VARIANT_NOT_FOUND, "That product option doesn't exist."));
        var cart = carts.findByCartKey(key.value()).orElseGet(() -> carts.save(new Cart(key.value())));

        var existing = cart.findByVariant(variantId);
        int newQuantity = existing.map(CartItem::getQuantity).orElse(0) + quantity;
        checkQuantity(variant, newQuantity);
        existing.ifPresentOrElse(item -> item.setQuantity(newQuantity), () -> cart.add(variantId, newQuantity));

        return toView(carts.saveAndFlush(cart));
    }

    @Override
    public CartView updateItem(CartKey key, long itemId, int quantity) {
        if (quantity < 0) {
            throw new CartException(Reason.INVALID_QUANTITY, "Quantity can't be negative.");
        }
        var cart = carts.findByCartKey(key.value())
                .orElseThrow(() -> new CartException(Reason.ITEM_NOT_FOUND, "That item isn't in your cart."));
        var item = cart.findItem(itemId)
                .orElseThrow(() -> new CartException(Reason.ITEM_NOT_FOUND, "That item isn't in your cart."));

        if (quantity == 0) {
            cart.remove(item);
        }
        else {
            var variant = catalog.findVariant(item.getVariantId())
                    .orElseThrow(() -> new CartException(Reason.VARIANT_NOT_FOUND, "That product is no longer sold."));
            checkQuantity(variant, quantity);
            item.setQuantity(quantity);
        }
        return toView(carts.saveAndFlush(cart));
    }

    @Override
    public CartView applyCoupon(CartKey key, String code) {
        var normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        coupons.findById(normalized)
                .filter(c -> c.isUsable(Instant.now()))
                .orElseThrow(() -> new CartException(Reason.INVALID_COUPON, "That coupon code isn't valid."));
        var cart = carts.findByCartKey(key.value()).orElseGet(() -> carts.save(new Cart(key.value())));
        cart.setCouponCode(normalized);
        return toView(carts.saveAndFlush(cart));
    }

    @Override
    public CartView removeCoupon(CartKey key) {
        return carts.findByCartKey(key.value())
                .map(cart -> {
                    cart.setCouponCode(null);
                    return toView(carts.saveAndFlush(cart));
                })
                .orElseGet(DefaultCartService::empty);
    }

    @Override
    public void clear(CartKey key) {
        carts.findByCartKey(key.value()).ifPresent(cart -> {
            cart.clear();
            carts.saveAndFlush(cart);
        });
    }

    @Override
    public CartKey resolve(Long customerId, CartKey cookieKey) {
        var cookieCart = cookieKey == null ? Optional.<Cart>empty() : carts.findByCartKey(cookieKey.value());

        if (customerId == null) {
            boolean ownedByCustomer = cookieCart.map(c -> c.getCustomerId() != null).orElse(false);
            if (cookieKey == null || ownedByCustomer) {
                return CartKey.random();
            }
            return cookieKey;
        }

        var own = carts.findByCustomerId(customerId);
        var anonymous = cookieCart.filter(c -> c.getCustomerId() == null);
        if (own.isEmpty()) {
            var cart = anonymous.orElseGet(() -> new Cart(CartKey.random().value()));
            cart.assignTo(customerId);
            return new CartKey(carts.saveAndFlush(cart).getCartKey());
        }
        anonymous.ifPresent(source -> mergeInto(own.get(), source));
        return new CartKey(own.get().getCartKey());
    }

    /** Moves items from an anonymous cart into the customer's, keeping within stock and limits. */
    private void mergeInto(Cart target, Cart source) {
        Map<Long, VariantView> variants = catalog.findVariants(
                source.getItems().stream().map(CartItem::getVariantId).toList()).stream()
                .collect(Collectors.toMap(VariantView::id, Function.identity()));
        for (var item : source.getItems()) {
            var variant = variants.get(item.getVariantId());
            if (variant == null) {
                continue;
            }
            var existing = target.findByVariant(item.getVariantId());
            int wanted = existing.map(CartItem::getQuantity).orElse(0) + item.getQuantity();
            int allowed = Math.min(wanted, Math.min(MAX_QUANTITY_PER_ITEM, variant.stock()));
            if (allowed <= 0) {
                continue;
            }
            existing.ifPresentOrElse(i -> i.setQuantity(allowed), () -> target.add(item.getVariantId(), allowed));
        }
        if (target.getCouponCode() == null && source.getCouponCode() != null) {
            target.setCouponCode(source.getCouponCode());
        }
        carts.delete(source);
        carts.saveAndFlush(target);
    }

    private static void checkQuantity(VariantView variant, int quantity) {
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new CartException(Reason.INVALID_QUANTITY,
                    "You can add up to " + MAX_QUANTITY_PER_ITEM + " of the same item.");
        }
        if (quantity > variant.stock()) {
            var message = variant.stock() == 0
                    ? variant.productName() + " in this option is sold out."
                    : "Only " + variant.stock() + " left of " + variant.productName() + " in this option.";
            throw new CartException(Reason.OUT_OF_STOCK, message);
        }
    }

    private CartView toView(Cart cart) {
        List<Long> variantIds = cart.getItems().stream().map(CartItem::getVariantId).toList();
        Map<Long, VariantView> variants = catalog.findVariants(variantIds).stream()
                .collect(Collectors.toMap(VariantView::id, Function.identity()));

        var lines = cart.getItems().stream()
                .filter(item -> variants.containsKey(item.getVariantId()))
                .map(item -> {
                    var v = variants.get(item.getVariantId());
                    return new CartView.Line(item.getId(), v.id(), v.productId(), v.productName(), v.size(), v.color(),
                            v.price(), item.getQuantity(), v.price().times(item.getQuantity()),
                            v.stock() >= item.getQuantity());
                })
                .toList();

        var subtotal = Money.usd(lines.stream().map(l -> l.lineTotal().amount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        var discount = Money.usd(BigDecimal.ZERO);
        var couponCode = cart.getCouponCode();
        if (couponCode != null) {
            var coupon = coupons.findById(couponCode).filter(c -> c.isUsable(Instant.now()));
            if (coupon.isPresent()) {
                discount = Money.usd(subtotal.amount()
                        .multiply(BigDecimal.valueOf(coupon.get().getPercentOff()))
                        .divide(BigDecimal.valueOf(100)));
            }
            else {
                couponCode = null;
            }
        }
        var total = Money.usd(subtotal.amount().subtract(discount.amount()));
        int count = lines.stream().mapToInt(CartView.Line::quantity).sum();
        return new CartView(lines, count, subtotal, couponCode, discount, total);
    }

    private static CartView empty() {
        var zero = Money.usd(BigDecimal.ZERO);
        return new CartView(List.of(), 0, zero, null, zero, zero);
    }
}
