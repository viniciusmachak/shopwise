package com.shopwise.assistant.internal;

import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.catalog.CatalogService;
import com.shopwise.order.CheckoutService;
import com.shopwise.order.OrderService;
import com.shopwise.recommendation.RecommendationService;
import com.shopwise.search.SearchService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Flux;

@Service
class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final ChatClient chatClient;
    private final SearchService search;
    private final CatalogService catalog;
    private final CartService carts;
    private final AuditLog audit;
    private final CheckoutService checkout;
    private final OrderService orders;
    private final PendingActions pending;
    private final ChatMemoryRepository memoryRepository;
    private final Timer firstTokenTimer;
    private final RecommendationService recommendations;
    private final String systemPrompt;
    private final String voicePrompt;

    AssistantService(RoutingChatModel chatModel, ChatMemoryRepository memoryRepository, SearchService search,
            CatalogService catalog, CartService carts, AuditLog audit, CheckoutService checkout, OrderService orders,
            PendingActions pending, MeterRegistry meters, RecommendationService recommendations,
            @Value("classpath:prompts/system.md") Resource systemPrompt,
            @Value("classpath:prompts/voice.md") Resource voicePrompt) throws IOException {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(memoryRepository)
                .maxMessages(20)
                .build();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
        this.search = search;
        this.catalog = catalog;
        this.carts = carts;
        this.audit = audit;
        this.checkout = checkout;
        this.orders = orders;
        this.pending = pending;
        this.memoryRepository = memoryRepository;
        this.recommendations = recommendations;
        this.firstTokenTimer = Timer.builder("shopwise.assistant.first.token")
                .description("Time from a customer message to the first token of the reply")
                .register(meters);
        this.systemPrompt = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.voicePrompt = voicePrompt.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * @param customerId the signed-in customer, captured from the request's security context
     */
    Flux<ServerSentEvent<Object>> reply(UUID conversationId, CartKey cartKey, @Nullable Long customerId, String text,
            boolean voice) {
        var ui = new UiEvents();
        var tools = new ShopTools(search, catalog, carts, audit, cartKey, conversationId, ui, recommendations);
        var orderTools = new OrderTools(customerId, cartKey, conversationId, ui, checkout, orders, pending, audit);
        var system = (voice ? systemPrompt + "\n\n" + voicePrompt : systemPrompt)
                + confirmOrRemind(customerId, conversationId, text, orderTools);

        var started = System.nanoTime();
        var firstToken = new AtomicBoolean();
        Flux<ServerSentEvent<Object>> tokens = chatClient.prompt()
                .system(system)
                .user(text)
                .tools(tools, orderTools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId.toString()))
                .stream()
                .content()
                .doOnNext(chunk -> {
                    if (firstToken.compareAndSet(false, true)) {
                        firstTokenTimer.record(Duration.ofNanos(System.nanoTime() - started));
                    }
                })
                .map(chunk -> event("token", Map.of("text", chunk)))
                .onErrorResume(e -> {
                    log.warn("Assistant reply failed for conversation {}", conversationId, e);
                    return Flux.just(error(e instanceof ChatUnavailableException
                            ? AssistantError.LLM_UNAVAILABLE
                            : AssistantError.INTERNAL));
                })
                .doFinally(signal -> ui.complete());

        return Flux.merge(ui.flux(), tokens)
                .concatWith(Flux.just(event("done", Map.of("conversationId", conversationId))));
    }

    /**
     * Tool results don't stay in the chat history, so on the next turn the model wouldn't know a
     * summary is waiting for the user's answer. The server tells it, from the stored pending actions.
     */
    private String confirmOrRemind(@Nullable Long customerId, UUID conversationId, String text, OrderTools tools) {
        if (customerId == null) {
            return "";
        }
        var waiting = pending.waiting(customerId, conversationId);
        if (waiting.isEmpty()) {
            return "";
        }
        // A clear "yes" to the summary on screen is the same as pressing Confirm: carry it out on the
        // server rather than hoping the model calls the tool, then let the model tell the user.
        if (ConfirmationIntent.isClearYes(text)) {
            var result = switch (waiting.getFirst().type()) {
                case CHECKOUT -> tools.confirmCheckout();
                case CANCEL_ORDER -> tools.confirmCancel();
            };
            return "\n\nThe user just confirmed and the store already carried it out. Result: " + result
                    + "\nTell the user what happened in a sentence or two. Don't call confirmCheckout or confirmCancel again.";
        }
        var lines = new StringBuilder("\n\nWaiting for the user's answer in this conversation:");
        waiting.stream().map(PendingActions.Waiting::type).distinct().forEach(type -> lines.append(switch (type) {
            case CHECKOUT -> "\n- A checkout summary was shown. If the user's latest message confirms it, call confirmCheckout now.";
            case CANCEL_ORDER -> "\n- A cancellation was shown. If the user's latest message confirms it, call confirmCancel now.";
        }));
        return lines.toString();
    }

    record HistoryMessage(String role, String text) {
    }

    /** The user and assistant messages of a conversation, oldest first. */
    List<HistoryMessage> history(UUID conversationId) {
        return memoryRepository.findByConversationId(conversationId.toString()).stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .filter(m -> m.getText() != null && !m.getText().isBlank())
                .map(m -> new HistoryMessage(m.getMessageType() == MessageType.USER ? "user" : "assistant", m.getText()))
                .toList();
    }

    static ServerSentEvent<Object> error(AssistantError error) {
        return event("error", Map.of("code", error.name(), "message", error.message()));
    }

    private static ServerSentEvent<Object> event(String type, Object data) {
        return ServerSentEvent.builder(data).event(type).build();
    }
}
