package com.shopwise.order.internal;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.shopwise.catalog.Money;
import com.shopwise.order.CheckoutQuote.ShippingChoice;
import com.shopwise.order.ShippingOption;

/**
 * Fixed shipping table: standard is free from $100 after discounts, express is a flat fee.
 */
@Component
class ShippingRates {

    static final BigDecimal FREE_STANDARD_FROM = new BigDecimal("100.00");
    static final BigDecimal STANDARD = new BigDecimal("5.00");
    static final BigDecimal EXPRESS = new BigDecimal("15.00");

    Money price(ShippingOption option, BigDecimal merchandiseTotal) {
        return Money.usd(switch (option) {
            case STANDARD -> merchandiseTotal.compareTo(FREE_STANDARD_FROM) >= 0 ? BigDecimal.ZERO : STANDARD;
            case EXPRESS -> EXPRESS;
        });
    }

    List<ShippingChoice> choices(BigDecimal merchandiseTotal) {
        return List.of(
                new ShippingChoice(ShippingOption.STANDARD, "Standard (3-5 business days)",
                        price(ShippingOption.STANDARD, merchandiseTotal)),
                new ShippingChoice(ShippingOption.EXPRESS, "Express (1-2 business days)",
                        price(ShippingOption.EXPRESS, merchandiseTotal)));
    }
}
