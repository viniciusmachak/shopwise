package com.shopwise.assistant.internal;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the chat models for both modes. Cloud providers speak the OpenAI-compatible API.
 */
@Configuration(proxyBeanMethods = false)
class ChatModelsConfiguration {

    @Bean
    RoutingChatModel routingChatModel(AiProperties properties, ToolCallingManager toolCallingManager) {
        var local = new NamedChatModel("ollama", OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(properties.local().baseUrl()).build())
                .options(OllamaChatOptions.builder().model(properties.local().model()).temperature(0.2).build())
                .toolCallingManager(toolCallingManager)
                .build());

        List<NamedChatModel> cloud = new ArrayList<>();
        for (var provider : properties.cloud().providers()) {
            if (provider.configured()) {
                cloud.add(new NamedChatModel(provider.name(), OpenAiChatModel.builder()
                        .options(OpenAiChatOptions.builder()
                                .baseUrl(provider.baseUrl())
                                .apiKey(provider.apiKey())
                                .model(provider.model())
                                .temperature(0.2)
                                .build())
                        .toolCallingManager(toolCallingManager)
                        .build()));
            }
        }
        return new RoutingChatModel(new ChatModeSettings(properties.chatMode()), properties.fallbackToLocal(), cloud,
                local);
    }
}
