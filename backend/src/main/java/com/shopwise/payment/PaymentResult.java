package com.shopwise.payment;

import org.jspecify.annotations.Nullable;

public record PaymentResult(boolean approved, String reference, @Nullable String declineReason) {
}
