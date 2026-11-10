package com.shopwise.assistant.internal;

import java.time.Duration;

import org.springframework.http.codec.ServerSentEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Events emitted by tools while the model is running, streamed to the UI next to the text.
 */
class UiEvents {

    private final Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().unicast().onBackpressureBuffer();

    void emit(String type, Object data) {
        sink.emitNext(ServerSentEvent.builder(data).event(type).build(),
                Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(200)));
    }

    void complete() {
        sink.tryEmitComplete();
    }

    Flux<ServerSentEvent<Object>> flux() {
        return sink.asFlux();
    }
}
