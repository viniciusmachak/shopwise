package com.shopwise.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.shopwise.assistant.ChatMode;

import reactor.core.publisher.Flux;

class RoutingChatModelTests {

    static class FakeModel implements ChatModel {

        final String reply;
        final boolean fails;
        final AtomicInteger calls = new AtomicInteger();

        FakeModel(String reply, boolean fails) {
            this.reply = reply;
            this.fails = fails;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            if (fails) {
                throw new IllegalStateException("429 quota exceeded");
            }
            return response(reply);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            calls.incrementAndGet();
            return fails ? Flux.error(new IllegalStateException("429 quota exceeded")) : Flux.just(response(reply));
        }

        static ChatResponse response(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }

    private static String text(ChatResponse response) {
        return response.getResult().getOutput().getText();
    }

    @Test
    void cloudModeFallsBackToTheNextProvider() {
        var gemini = new FakeModel("gemini", true);
        var groq = new FakeModel("groq", false);
        var router = new RoutingChatModel(new ChatModeSettings(ChatMode.CLOUD), true,
                List.of(new NamedChatModel("gemini", gemini), new NamedChatModel("groq", groq)),
                new NamedChatModel("ollama", new FakeModel("ollama", false)));

        assertThat(text(router.call(new Prompt("hi")))).isEqualTo("groq");
        assertThat(gemini.calls).hasValue(1);
    }

    @Test
    void cloudModeFallsBackToLocalWhenEnabled() {
        var router = new RoutingChatModel(new ChatModeSettings(ChatMode.CLOUD), true,
                List.of(new NamedChatModel("gemini", new FakeModel("gemini", true))),
                new NamedChatModel("ollama", new FakeModel("ollama", false)));

        assertThat(text(router.call(new Prompt("hi")))).isEqualTo("ollama");
    }

    @Test
    void cloudModeWithoutLocalFallbackFails() {
        var router = new RoutingChatModel(new ChatModeSettings(ChatMode.CLOUD), false,
                List.of(new NamedChatModel("gemini", new FakeModel("gemini", true))),
                new NamedChatModel("ollama", new FakeModel("ollama", false)));

        assertThatThrownBy(() -> router.call(new Prompt("hi"))).isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void cloudModeWithNoKeysUsesLocal() {
        var router = new RoutingChatModel(new ChatModeSettings(ChatMode.CLOUD), false, List.of(),
                new NamedChatModel("ollama", new FakeModel("ollama", false)));

        assertThat(text(router.call(new Prompt("hi")))).isEqualTo("ollama");
    }

    @Test
    void localModeNeverCallsTheCloud() {
        var gemini = new FakeModel("gemini", false);
        var router = new RoutingChatModel(new ChatModeSettings(ChatMode.LOCAL), true,
                List.of(new NamedChatModel("gemini", gemini)),
                new NamedChatModel("ollama", new FakeModel("ollama", false)));

        assertThat(text(router.call(new Prompt("hi")))).isEqualTo("ollama");
        assertThat(gemini.calls).hasValue(0);
    }

    @Test
    void switchingModeTakesEffectOnTheNextRequest() {
        var settings = new ChatModeSettings(ChatMode.LOCAL);
        var router = new RoutingChatModel(settings, false,
                List.of(new NamedChatModel("gemini", new FakeModel("gemini", false))),
                new NamedChatModel("ollama", new FakeModel("ollama", false)));

        settings.change(ChatMode.CLOUD);

        assertThat(text(router.call(new Prompt("hi")))).isEqualTo("gemini");
    }

    @Test
    void streamingFallsBackWhenTheFirstModelFailsBeforeAnyOutput() {
        var router = new RoutingChatModel(new ChatModeSettings(ChatMode.CLOUD), true,
                List.of(new NamedChatModel("gemini", new FakeModel("gemini", true))),
                new NamedChatModel("ollama", new FakeModel("ollama", false)));

        assertThat(router.stream(new Prompt("hi")).map(RoutingChatModelTests::text).collectList().block())
                .containsExactly("ollama");
    }
}
