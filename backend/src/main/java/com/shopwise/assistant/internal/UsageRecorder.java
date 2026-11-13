package com.shopwise.assistant.internal;

/**
 * Receives one entry per chat model attempt: which provider, whether it worked, and the tokens it reported.
 */
interface UsageRecorder {

    UsageRecorder NONE = (provider, ok, promptTokens, completionTokens) -> {
    };

    void record(String provider, boolean ok, long promptTokens, long completionTokens);
}
