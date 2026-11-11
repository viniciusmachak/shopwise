package com.shopwise.assistant.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shopwise.cart.CartKey;
import com.shopwise.customer.CurrentCustomer;
import com.shopwise.customer.CustomerRef;
import com.shopwise.order.CheckoutException;

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
    private final PendingActions pending;

    AssistantController(AssistantService assistant, ConversationRepository conversations, RateLimiter rateLimiter,
            PendingActions pending) {
        this.assistant = assistant;
        this.conversations = conversations;
        this.rateLimiter = rateLimiter;
        this.pending = pending;
    }

    @PostMapping(path = "/conversations/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Object>> message(@PathVariable UUID conversationId, CartKey cartKey,
            @Valid @RequestBody Message body) {
        var customerId = CurrentCustomer.get().map(CustomerRef::id).orElse(null);
        if (!rateLimiter.tryAcquire(cartKey.value())) {
            return Flux.just(AssistantService.error(AssistantError.RATE_LIMITED));
        }
        if (!conversations.claim(conversationId, cartKey.value(), customerId)) {
            return Flux.just(AssistantService.error(AssistantError.FORBIDDEN));
        }
        return assistant.reply(conversationId, cartKey, customerId, body.text(), body.mode() == Mode.voice);
    }

    @GetMapping("/conversations")
    List<ConversationRepository.Summary> conversations(CartKey cartKey) {
        return conversations.list(cartKey.value(), CurrentCustomer.get().map(CustomerRef::id).orElse(null));
    }

    @GetMapping("/conversations/{conversationId}")
    List<AssistantService.HistoryMessage> conversation(@PathVariable UUID conversationId, CartKey cartKey) {
        var customerId = CurrentCustomer.get().map(CustomerRef::id).orElse(null);
        if (!conversations.owns(conversationId, cartKey.value(), customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found.");
        }
        return assistant.history(conversationId);
    }

    /** The Confirm button: carries out a prepared action without going through the model. */
    @PostMapping("/pending-actions/{token}/confirm")
    PendingActions.Confirmed confirm(@PathVariable String token) {
        return pending.confirm(token, CurrentCustomer.require().id());
    }

    @PostMapping("/pending-actions/{token}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discard(@PathVariable String token) {
        pending.discard(token, CurrentCustomer.require().id());
    }

    @ExceptionHandler
    ProblemDetail handle(PendingActionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(CheckoutException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setProperty("reason", e.reason().name());
        return problem;
    }

    enum Mode { chat, voice }

    record Message(@NotBlank @Size(max = 1000) String text, Mode mode) {
    }
}
