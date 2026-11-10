package com.shopwise.assistant;

/**
 * Where the chat model runs.
 */
public enum ChatMode {
    /** Free-tier cloud APIs (Gemini, then Groq). */
    CLOUD,
    /** Ollama on our own machine. */
    LOCAL
}
