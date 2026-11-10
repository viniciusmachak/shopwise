package com.shopwise.assistant.internal;

import org.springframework.ai.chat.model.ChatModel;

record NamedChatModel(String name, ChatModel model) {
}
