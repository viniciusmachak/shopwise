package com.shopwise.assistant.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.shopwise.cart.CartKey;
import com.shopwise.cart.CartService;
import com.shopwise.catalog.CatalogService;
import com.shopwise.search.SearchService;

import reactor.core.publisher.Flux;

@Service
class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final ChatClient chatClient;
    private final SearchService search;
    private final CatalogService catalog;
    private final CartService carts;
    private final AuditLog audit;
    private final String systemPrompt;
    private final String voicePrompt;

    AssistantService(RoutingChatModel chatModel, ChatMemoryRepository memoryRepository, SearchService search,
            CatalogService catalog, CartService carts, AuditLog audit,
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
        this.systemPrompt = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.voicePrompt = voicePrompt.getContentAsString(StandardCharsets.UTF_8);
    }

    Flux<ServerSentEvent<Object>> reply(UUID conversationId, CartKey cartKey, String text, boolean voice) {
        var ui = new UiEvents();
        var tools = new ShopTools(search, catalog, carts, audit, cartKey, conversationId, ui);
        var system = voice ? systemPrompt + "\n\n" + voicePrompt : systemPrompt;

        Flux<ServerSentEvent<Object>> tokens = chatClient.prompt()
                .system(system)
                .user(text)
                .tools(tools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId.toString()))
                .stream()
                .content()
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

    static ServerSentEvent<Object> error(AssistantError error) {
        return event("error", Map.of("code", error.name(), "message", error.message()));
    }

    private static ServerSentEvent<Object> event(String type, Object data) {
        return ServerSentEvent.builder(data).event(type).build();
    }
}
