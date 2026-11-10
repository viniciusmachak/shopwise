package com.shopwise.assistant.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import com.shopwise.assistant.ChatMode;

import reactor.core.publisher.Flux;

/**
 * Sends each request to the models of the current mode, in order, moving to the next one
 * when a model fails before producing output (quota, network, outage).
 */
class RoutingChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(RoutingChatModel.class);

    private final ChatModeSettings settings;
    private final boolean fallbackToLocal;
    private final List<NamedChatModel> cloud;
    private final NamedChatModel local;

    RoutingChatModel(ChatModeSettings settings, boolean fallbackToLocal, List<NamedChatModel> cloud,
            NamedChatModel local) {
        this.settings = settings;
        this.fallbackToLocal = fallbackToLocal;
        this.cloud = List.copyOf(cloud);
        this.local = local;
    }

    ChatModeSettings settings() {
        return settings;
    }

    List<NamedChatModel> candidates() {
        if (settings.current() == ChatMode.LOCAL) {
            return List.of(local);
        }
        var models = new ArrayList<>(cloud);
        if (fallbackToLocal || models.isEmpty()) {
            models.add(local);
        }
        return models;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        RuntimeException last = null;
        for (var candidate : candidates()) {
            try {
                return candidate.model().call(retarget(prompt, candidate.model()));
            }
            catch (RuntimeException e) {
                log.warn("Chat model {} failed: {}", candidate.name(), e.getMessage());
                last = e;
            }
        }
        throw new ChatUnavailableException(last);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return streamFrom(candidates(), 0, prompt, null);
    }

    private Flux<ChatResponse> streamFrom(List<NamedChatModel> models, int index, Prompt prompt, Throwable last) {
        if (index >= models.size()) {
            return Flux.error(new ChatUnavailableException(last));
        }
        var candidate = models.get(index);
        var emitted = new AtomicBoolean();
        return Flux.defer(() -> candidate.model().stream(retarget(prompt, candidate.model())))
                .doOnNext(r -> emitted.set(true))
                .onErrorResume(e -> {
                    if (emitted.get()) {
                        return Flux.error(e);
                    }
                    log.warn("Chat model {} failed: {}", candidate.name(), e.getMessage());
                    return streamFrom(models, index + 1, prompt, e);
                });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    /**
     * Moves the portable options chosen by ChatClient (tools, tool context, temperature)
     * onto the target model's own option type.
     */
    private static Prompt retarget(Prompt prompt, ChatModel target) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions requested)) {
            return prompt;
        }
        var builder = target.getDefaultOptions().mutate();
        if (builder instanceof ToolCallingChatOptions.Builder<?> tools) {
            tools.toolCallbacks(requested.getToolCallbacks());
            tools.toolContext(requested.getToolContext());
        }
        if (requested.getTemperature() != null) {
            builder.temperature(requested.getTemperature());
        }
        return prompt.mutate().chatOptions(builder.build()).build();
    }
}
