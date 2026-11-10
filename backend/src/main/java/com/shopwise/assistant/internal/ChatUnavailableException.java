package com.shopwise.assistant.internal;

class ChatUnavailableException extends RuntimeException {

    ChatUnavailableException(Throwable cause) {
        super("No chat model is available right now", cause);
    }
}
