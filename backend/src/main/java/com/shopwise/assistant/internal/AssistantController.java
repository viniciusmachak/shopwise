package com.shopwise.assistant.internal;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopwise.cart.CartKey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/assistant")
class AssistantController {

    private final AssistantService assistant;
    private final ConversationRepository conversations;
    private final RateLimiter rateLimiter;

    AssistantController(AssistantService assistant, ConversationRepository conversations, RateLimiter rateLimiter) {
        this.assistant = assistant;
        this.conversations = conversations;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(path = "/conversations/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Object>> message(@PathVariable UUID conversationId, CartKey cartKey,
            @Valid @RequestBody Message body) {
        if (!rateLimiter.tryAcquire(cartKey.value())) {
            return Flux.just(AssistantService.error(AssistantError.RATE_LIMITED));
        }
        if (!conversations.claim(conversationId, cartKey.value())) {
            return Flux.just(AssistantService.error(AssistantError.FORBIDDEN));
        }
        return assistant.reply(conversationId, cartKey, body.text(), body.mode() == Mode.voice);
    }

    enum Mode { chat, voice }

    record Message(@NotBlank @Size(max = 1000) String text, Mode mode) {
    }
}
