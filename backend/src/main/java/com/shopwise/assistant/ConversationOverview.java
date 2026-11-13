package com.shopwise.assistant;

import java.time.Instant;
import java.util.UUID;

public record ConversationOverview(UUID id, Instant createdAt, String customer, long userMessages,
        String firstQuestion, boolean placedOrder) {
}
