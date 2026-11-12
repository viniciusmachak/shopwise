package com.shopwise.catalog;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VariantForm(
        @NotBlank @Size(max = 80) String sku,
        @Nullable @Size(max = 20) String size,
        @Nullable @Size(max = 40) String color,
        @Nullable @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal priceOverride) {
}
