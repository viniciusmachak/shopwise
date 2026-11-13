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
    private final UsageRecorder usage;

    RoutingChatModel(ChatModeSettings settings, boolean fallbackToLocal, List<NamedChatModel> cloud,
            NamedChatModel local) {
        this(settings, fallbackToLocal, cloud, local, UsageRecorder.NONE);
    }

    RoutingChatModel(ChatModeSettings settings, boolean fallbackToLocal, List<NamedChatModel> cloud,
            NamedChatModel local, UsageRecorder usage) {
        this.settings = settings;
        this.fallbackToLocal = fallbackToLocal;
        this.cloud = List.copyOf(cloud);
        this.local = local;
        this.usage = usage;
    }

    List<String> cloudProviders() {
        return cloud.stream().map(NamedChatModel::name).toList();
    }

    String localName() {
        return local.name();
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
                var response = candidate.model().call(retarget(prompt, candidate.model()));
                var tokens = tokens(response);
                usage.record(candidate.name(), true, tokens[0], tokens[1]);
                return response;
            }
            catch (RuntimeException e) {
                log.warn("Chat model {} failed: {}", candidate.name(), e.getMessage());
                usage.record(candidate.name(), false, 0, 0);
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
        var tokens = new long[2];
        return Flux.defer(() -> candidate.model().stream(retarget(prompt, candidate.model())))
                .doOnNext(r -> {
                    emitted.set(true);
                    var t = tokens(r);
                    tokens[0] = Math.max(tokens[0], t[0]);
                    tokens[1] = Math.max(tokens[1], t[1]);
                })
                .doOnComplete(() -> usage.record(candidate.name(), true, tokens[0], tokens[1]))
                .onErrorResume(e -> {
                    usage.record(candidate.name(), false, tokens[0], tokens[1]);
                    if (emitted.get()) {
                        return Flux.error(e);
                    }
                    log.warn("Chat model {} failed: {}", candidate.name(), e.getMessage());
                    return streamFrom(models, index + 1, prompt, e);
                });
    }

    /** Prompt and completion tokens the provider reported, or zeros. */
    private static long[] tokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return new long[2];
        }
        var u = response.getMetadata().getUsage();
        return new long[] { u.getPromptTokens() == null ? 0 : u.getPromptTokens(),
                u.getCompletionTokens() == null ? 0 : u.getCompletionTokens() };
    }

    @Override
    public ChatOptions getOptions() {
        // Portable options: ChatClient fills in tools here, and retarget() moves them
        // onto the chosen model's own option type.
        return ToolCallingChatOptions.builder().build();
    }

    /**
     * Moves the portable options chosen by ChatClient (tools, tool context, temperature)
     * onto the target model's own option type.
     */
    static Prompt retarget(Prompt prompt, ChatModel target) {
        var requested = prompt.getOptions();
        var builder = target.getDefaultOptions().mutate();
        if (requested instanceof ToolCallingChatOptions tooling
                && builder instanceof ToolCallingChatOptions.Builder<?> tools) {
            tools.toolCallbacks(tooling.getToolCallbacks());
            tools.toolContext(tooling.getToolContext());
        }
        if (requested != null && requested.getTemperature() != null) {
            builder.temperature(requested.getTemperature());
        }
        log.debug("Routing to {} with options {}", target.getClass().getSimpleName(),
                requested == null ? "none" : requested.getClass().getSimpleName());
        return prompt.mutate().chatOptions(builder.build()).build();
    }
}
