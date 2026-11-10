package com.shopwise.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount, String currency) {

    public Money {
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money usd(BigDecimal amount) {
        return new Money(amount, "USD");
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }
}
