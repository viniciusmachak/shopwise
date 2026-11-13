package com.shopwise.assistant.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcUsageRecorder implements UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(JdbcUsageRecorder.class);

    private final JdbcClient jdbc;

    JdbcUsageRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String provider, boolean ok, long promptTokens, long completionTokens) {
        try {
            jdbc.sql("""
                    insert into assistant_llm_usage (day, provider, requests, failures, prompt_tokens, completion_tokens)
                    values (current_date, :provider, 1, :failure, :prompt, :completion)
                    on conflict (day, provider) do update set
                        requests = assistant_llm_usage.requests + 1,
                        failures = assistant_llm_usage.failures + excluded.failures,
                        prompt_tokens = assistant_llm_usage.prompt_tokens + excluded.prompt_tokens,
                        completion_tokens = assistant_llm_usage.completion_tokens + excluded.completion_tokens
                    """)
                    .param("provider", provider)
                    .param("failure", ok ? 0 : 1)
                    .param("prompt", promptTokens)
                    .param("completion", completionTokens)
                    .update();
        }
        catch (RuntimeException e) {
            // Usage stats must never break a chat reply.
            log.warn("Could not record chat model usage: {}", e.getMessage());
        }
    }
}
