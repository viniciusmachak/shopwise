package com.shopwise.assistant.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.assistant.AssistantAdmin;
import com.shopwise.assistant.AssistantStats;
import com.shopwise.assistant.AssistantStatus;
import com.shopwise.assistant.ChatMode;
import com.shopwise.assistant.ConversationOverview;
import com.shopwise.assistant.TranscriptLine;
import com.shopwise.customer.CustomerDirectory;

@Service
class DefaultAssistantAdmin implements AssistantAdmin {

    static final String MODE_KEY = "ai.chat-mode";

    private static final Logger log = LoggerFactory.getLogger(DefaultAssistantAdmin.class);

    private final ChatModeSettings settings;
    private final AiProperties properties;
    private final JdbcClient jdbc;
    private final AssistantService assistant;
    private final CustomerDirectory customers;

    DefaultAssistantAdmin(ChatModeSettings settings, AiProperties properties, JdbcClient jdbc, AssistantService assistant,
            CustomerDirectory customers) {
        this.settings = settings;
        this.properties = properties;
        this.jdbc = jdbc;
        this.assistant = assistant;
        this.customers = customers;
    }

    /** A mode chosen in the backoffice wins over the configured default, also after restarts. */
    @EventListener(ApplicationReadyEvent.class)
    void restoreSavedMode() {
        jdbc.sql("select value from app_setting where key = :key").param("key", MODE_KEY)
                .query(String.class).optional()
                .ifPresent(saved -> {
                    try {
                        settings.change(ChatMode.valueOf(saved));
                    }
                    catch (IllegalArgumentException e) {
                        log.warn("Ignoring unknown saved chat mode {}", saved);
                    }
                });
    }

    @Override
    public AssistantStatus status() {
        var changedAt = jdbc.sql("select updated_at from app_setting where key = :key").param("key", MODE_KEY)
                .query(Timestamp.class).optional().map(Timestamp::toInstant).orElse(null);
        var providers = properties.cloud().providers().stream()
                .map(p -> new AssistantStatus.Provider(p.name(), p.model(), p.configured()))
                .toList();
        return new AssistantStatus(settings.current(), properties.chatMode(), changedAt, providers,
                properties.local().model(), properties.fallbackToLocal());
    }

    @Override
    @Transactional
    public AssistantStatus changeMode(ChatMode mode, long changedBy) {
        jdbc.sql("""
                insert into app_setting (key, value, updated_at, updated_by) values (:key, :value, now(), :by)
                on conflict (key) do update set value = excluded.value, updated_at = now(), updated_by = excluded.updated_by
                """)
                .param("key", MODE_KEY)
                .param("value", mode.name())
                .param("by", changedBy)
                .update();
        settings.change(mode);
        log.info("Chat mode changed to {} by customer {}", mode, changedBy);
        return status();
    }

    @Override
    public AssistantStats stats(int days) {
        int window = Math.clamp(days, 1, 365);
        var since = Timestamp.from(Instant.now().minusSeconds(window * 86_400L));

        long conversations = count("""
                select count(distinct c.id) from assistant_conversation c
                where c.created_at >= :since
                  and exists (select 1 from spring_ai_chat_memory m where m.conversation_id = c.id::text and m.type = 'USER')
                """, since);
        long userMessages = count("""
                select count(*) from spring_ai_chat_memory where type = 'USER' and "timestamp" >= :since
                """, since);
        long prepared = count("select count(*) from pending_action where type = 'CHECKOUT' and created_at >= :since", since);
        long ordered = count("""
                select count(*) from pending_action
                where type = 'CHECKOUT' and result_order_id is not null and created_at >= :since
                """, since);
        long convertedConversations = count("""
                select count(distinct conversation_id) from pending_action
                where type = 'CHECKOUT' and result_order_id is not null and conversation_id is not null and created_at >= :since
                """, since);

        var tools = jdbc.sql("""
                select action as tool, count(*) filter (where outcome = 'OK') as ok,
                       count(*) filter (where outcome <> 'OK') as rejected
                from audit_log where created_at >= :since group by action order by count(*) desc
                """).param("since", since).query(AssistantStats.ToolUse.class).list();
        var usage = jdbc.sql("""
                select provider, sum(requests) as requests, sum(failures) as failures,
                       sum(prompt_tokens) as prompt_tokens, sum(completion_tokens) as completion_tokens
                from assistant_llm_usage where day >= cast(:since as date) group by provider order by provider
                """).param("since", since).query(AssistantStats.ProviderUsage.class).list();

        double conversion = conversations == 0 ? 0 : (double) convertedConversations / conversations;
        return new AssistantStats(window, conversations, userMessages, prepared, ordered, conversion, tools, usage);
    }

    @Override
    public List<ConversationOverview> recentConversations(int limit) {
        return jdbc.sql("""
                select c.id, c.created_at, c.customer_id,
                       (select count(*) from spring_ai_chat_memory m
                        where m.conversation_id = c.id::text and m.type = 'USER') as user_messages,
                       (select left(m.content, 120) from spring_ai_chat_memory m
                        where m.conversation_id = c.id::text and m.type = 'USER' order by m.sequence_id limit 1) as first_question,
                       exists (select 1 from pending_action p where p.conversation_id = c.id and p.result_order_id is not null
                               and p.type = 'CHECKOUT') as placed_order
                from assistant_conversation c
                where exists (select 1 from spring_ai_chat_memory m where m.conversation_id = c.id::text and m.type = 'USER')
                order by c.created_at desc
                limit :limit
                """)
                .param("limit", Math.clamp(limit, 1, 200))
                .query((rs, n) -> {
                    var customerId = (Long) rs.getObject("customer_id");
                    var who = customerId == null ? "Visitor"
                            : customers.contact(customerId).map(c -> c.email()).orElse("Deleted account");
                    return new ConversationOverview(rs.getObject("id", UUID.class),
                            rs.getTimestamp("created_at").toInstant(), who, rs.getLong("user_messages"),
                            rs.getString("first_question"), rs.getBoolean("placed_order"));
                })
                .list();
    }

    @Override
    public List<TranscriptLine> transcript(UUID conversationId) {
        return assistant.history(conversationId).stream().map(m -> new TranscriptLine(m.role(), m.text())).toList();
    }

    private long count(String sql, Timestamp since) {
        return jdbc.sql(sql).param("since", since).query(Long.class).single();
    }
}
