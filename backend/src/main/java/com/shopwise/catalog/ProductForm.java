package com.shopwise.catalog;

import java.math.BigDecimal;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductForm(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 120) String brand,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank String category,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Nullable Map<String, Object> attributes,
        @Nullable @Size(max = 500) String imageUrl) {
}
