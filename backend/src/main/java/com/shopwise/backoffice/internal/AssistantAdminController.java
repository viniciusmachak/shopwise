package com.shopwise.backoffice.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopwise.assistant.AssistantAdmin;
import com.shopwise.assistant.AssistantStats;
import com.shopwise.assistant.AssistantStatus;
import com.shopwise.assistant.ChatMode;
import com.shopwise.assistant.ConversationOverview;
import com.shopwise.assistant.TranscriptLine;
import com.shopwise.customer.CurrentCustomer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Admin-only (secured under /api/admin). Chat mode switch (RF27) and assistant dashboard (RF25).
 */
@RestController
@RequestMapping("/api/admin/assistant")
class AssistantAdminController {

    private final AssistantAdmin assistant;

    AssistantAdminController(AssistantAdmin assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/mode")
    AssistantStatus status() {
        return assistant.status();
    }

    @PutMapping("/mode")
    AssistantStatus changeMode(@Valid @RequestBody ModeChange body) {
        return assistant.changeMode(body.mode(), CurrentCustomer.require().id());
    }

    @GetMapping("/stats")
    AssistantStats stats(@RequestParam(defaultValue = "7") int days) {
        return assistant.stats(days);
    }

    @GetMapping("/conversations")
    List<ConversationOverview> conversations(@RequestParam(defaultValue = "30") int limit) {
        return assistant.recentConversations(limit);
    }

    @GetMapping("/conversations/{id}")
    List<TranscriptLine> transcript(@PathVariable UUID id) {
        return assistant.transcript(id);
    }

    record ModeChange(@NotNull ChatMode mode) {
    }
}
