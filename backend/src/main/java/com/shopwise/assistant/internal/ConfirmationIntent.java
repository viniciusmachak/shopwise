package com.shopwise.assistant.internal;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognizes a short, unambiguous "yes" to a summary on screen. Anything longer or qualified
 * ("yes, but in size 10") is left to the model.
 */
final class ConfirmationIntent {

    private static final Pattern CLEAR_YES = Pattern.compile("""
            ^(yes|yeah|yep|yup|sure|ok|okay|confirm|confirmed|go ahead|do it|place it|place the order|place my order|\
            cancel it|yes please|yes go ahead|yes do it|yes confirm|yes place it|yes place the order|yes place my order|\
            yes cancel it|please confirm|please place the order|please place my order|please do)$""");

    private ConfirmationIntent() {
    }

    static boolean isClearYes(String message) {
        if (message == null) {
            return false;
        }
        var normalized = message.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .replaceAll(" (thanks|thank you|now)$", "");
        return normalized.split(" ").length <= 5 && CLEAR_YES.matcher(normalized).matches();
    }
}
