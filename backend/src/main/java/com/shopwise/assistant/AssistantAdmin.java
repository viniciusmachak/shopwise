package com.shopwise.assistant;

import java.util.List;
import java.util.UUID;

/**
 * Public API for the backoffice: switch the chat mode at runtime and look at assistant activity.
 */
public interface AssistantAdmin {

    AssistantStatus status();

    /** Takes effect on the next message; stored, so it survives restarts. */
    AssistantStatus changeMode(ChatMode mode, long changedBy);

    AssistantStats stats(int days);

    List<ConversationOverview> recentConversations(int limit);

    List<TranscriptLine> transcript(UUID conversationId);
}
