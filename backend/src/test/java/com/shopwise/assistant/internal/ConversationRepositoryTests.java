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
    void aConversationBelongsToTheCartThatStartedIt() {
        var conversation = UUID.randomUUID();
        var owner = UUID.randomUUID();

        assertThat(conversations.claim(conversation, owner)).isTrue();
        assertThat(conversations.claim(conversation, owner)).isTrue();
        assertThat(conversations.claim(conversation, UUID.randomUUID())).isFalse();
    }
}
