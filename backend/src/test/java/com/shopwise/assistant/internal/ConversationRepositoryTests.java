package com.shopwise.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.shopwise.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConversationRepositoryTests {

    @Autowired
    ConversationRepository conversations;

    @Test
    void aVisitorsConversationBelongsToTheirCart() {
        var conversation = UUID.randomUUID();
        var cart = UUID.randomUUID();

        assertThat(conversations.claim(conversation, cart, null)).isTrue();
        assertThat(conversations.claim(conversation, cart, null)).isTrue();
        assertThat(conversations.claim(conversation, UUID.randomUUID(), null)).isFalse();
    }

    @Test
    void signingInOnTheSameCartAdoptsTheConversation() {
        var conversation = UUID.randomUUID();
        var cart = UUID.randomUUID();
        conversations.claim(conversation, cart, null);

        assertThat(conversations.claim(conversation, cart, 42L)).isTrue();
        assertThat(conversations.claim(conversation, UUID.randomUUID(), 42L)).isTrue();
        assertThat(conversations.claim(conversation, cart, 43L)).isFalse();
        assertThat(conversations.owns(conversation, cart, null)).isFalse();
    }

    @Test
    void ownsNeverCreatesAConversation() {
        assertThat(conversations.owns(UUID.randomUUID(), UUID.randomUUID(), null)).isFalse();
    }
}
