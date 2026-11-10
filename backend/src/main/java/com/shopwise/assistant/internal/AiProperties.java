package com.shopwise.assistant.internal;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.shopwise.assistant.ChatMode;

@ConfigurationProperties("shopwise.ai")
public record AiProperties(ChatMode chatMode, boolean fallbackToLocal, Local local, Cloud cloud) {

    public AiProperties {
        chatMode = chatMode == null ? ChatMode.LOCAL : chatMode;
        cloud = cloud == null ? new Cloud(List.of()) : cloud;
    }

    public record Local(String baseUrl, String model) {
    }

    public record Cloud(List<Provider> providers) {

        public Cloud {
            providers = providers == null ? List.of() : providers;
        }
    }

    public record Provider(String name, String baseUrl, String apiKey, String model) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
