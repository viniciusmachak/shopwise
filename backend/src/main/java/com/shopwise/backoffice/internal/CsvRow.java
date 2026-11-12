package com.shopwise.backoffice.internal;

import java.util.Map;

/**
 * A raw CSV row with its line number, before validation.
 */
record CsvRow(int lineNumber, Map<String, String> values) {

    String get(String column) {
        var value = values.get(column);
        return value == null ? "" : value.trim();
    }
}
