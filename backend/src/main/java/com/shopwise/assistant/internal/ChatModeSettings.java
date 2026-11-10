package com.shopwise.assistant.internal;

import java.util.concurrent.atomic.AtomicReference;

import com.shopwise.assistant.ChatMode;

/**
 * Current chat mode. Starts from configuration; the backoffice will be able to change it at runtime.
 */
class ChatModeSettings {

    private final AtomicReference<ChatMode> mode;

    ChatModeSettings(ChatMode initial) {
        this.mode = new AtomicReference<>(initial);
    }

    ChatMode current() {
        return mode.get();
    }

    void change(ChatMode newMode) {
        mode.set(newMode);
    }
}
