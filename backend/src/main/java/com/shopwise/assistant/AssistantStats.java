package com.shopwise.assistant;

import java.util.List;

/**
 * Assistant activity over the last {@code days} days.
 *
 * @param conversionRate share of conversations in which an order was placed through the chat
 */
public record AssistantStats(
        int days,
        long conversations,
        long userMessages,
        long checkoutsPrepared,
        long ordersPlaced,
        double conversionRate,
        List<ToolUse> tools,
        List<ProviderUsage> usage) {

    public record ToolUse(String tool, long ok, long rejected) {
    }

    public record ProviderUsage(String provider, long requests, long failures, long promptTokens,
            long completionTokens) {
    }
}
