package com.shopwise.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfirmationIntentTests {

    @ParameterizedTest
    @ValueSource(strings = { "Yes", "yes!", "Yes, place the order", "OK", "go ahead", "Confirm", "yes please",
            "Place my order now", "yes, thanks", "Yep." })
    void recognizesAClearYes(String message) {
        assertThat(ConfirmationIntent.isClearYes(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "yes but in size 10", "no", "not yet", "what is the total?", "yes and add socks",
            "I don't think so", "place the order with express shipping", "", "maybe" })
    void leavesAnythingElseToTheModel(String message) {
        assertThat(ConfirmationIntent.isClearYes(message)).isFalse();
    }
}
