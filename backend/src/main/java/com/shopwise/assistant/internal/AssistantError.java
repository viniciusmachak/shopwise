package com.shopwise.assistant.internal;

enum AssistantError {

    LLM_UNAVAILABLE("The assistant is resting right now. You can keep shopping normally."),
    RATE_LIMITED("You're sending messages very fast. Please wait a moment."),
    FORBIDDEN("This conversation belongs to someone else."),
    INTERNAL("Something went wrong. Please try again.");

    private final String message;

    AssistantError(String message) {
        this.message = message;
    }

    String message() {
        return message;
    }
}
