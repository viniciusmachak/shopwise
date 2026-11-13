package com.shopwise.assistant;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Where the assistant's chat model runs now, and what is available.
 */
public record AssistantStatus(
        ChatMode mode,
        ChatMode configuredMode,
        @Nullable Instant changedAt,
        List<Provider> cloudProviders,
        String localModel,
        boolean fallbackToLocal) {

    public record Provider(String name, String model, boolean configured) {
    }
}
