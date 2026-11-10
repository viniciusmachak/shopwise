package com.shopwise.assistant.internal;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Records every assistant action that changes state.
 */
@Component
class AuditLog {

    private final JdbcClient jdbc;

    AuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void record(UUID cartKey, UUID conversationId, String action, Map<String, ?> arguments, boolean ok,
            String detail) {
        jdbc.sql("""
                insert into audit_log (cart_key, conversation_id, action, arguments, outcome, detail)
                values (:cartKey, :conversationId, :action, cast(:arguments as jsonb), :outcome, :detail)
                """)
                .param("cartKey", cartKey)
                .param("conversationId", conversationId)
                .param("action", action)
                .param("arguments", toJson(arguments))
                .param("outcome", ok ? "OK" : "REJECTED")
                .param("detail", detail)
                .update();
    }

    private static String toJson(Map<String, ?> arguments) {
        return arguments.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + value(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String value(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        return "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
