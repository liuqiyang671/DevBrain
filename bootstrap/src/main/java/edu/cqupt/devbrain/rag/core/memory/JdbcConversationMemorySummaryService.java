package edu.cqupt.devbrain.rag.core.memory;

import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 基于 PostgreSQL + Redisson 的对话摘要服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdbcConversationMemorySummaryService implements ConversationMemorySummaryService {

    private static final String LOCK_PREFIX = "devbrain:conversation:summary:lock:";
    private static final String DEFAULT_TEMPLATE = """
            You are maintaining a concise memory summary for a multi-turn RAG conversation.

            Existing summary:
            {{summary}}

            New messages:
            {{messages}}

            Rewrite the summary so it preserves user goals, important facts, decisions, constraints, and unresolved questions.
            Keep it concise and useful for future answer generation.
            """;

    private static final String LOAD_SUMMARY_SQL = """
            SELECT id, conversation_id, user_id, summary, message_count, last_summarized_message_id, create_time, update_time
              FROM t_conversation_summary
             WHERE conversation_id = ? AND user_id = ?
            """;

    private static final String COUNT_USER_MESSAGES_SQL = """
            SELECT COUNT(*)
              FROM t_message
             WHERE conversation_id = ? AND user_id = ? AND role = 'user'
            """;

    private static final String LOAD_PENDING_MESSAGES_WITHOUT_SUMMARY_SQL = """
            SELECT id, conversation_id, user_id, role, content, thinking_content, thinking_duration, create_time, update_time
              FROM t_message
             WHERE conversation_id = ? AND user_id = ?
             ORDER BY create_time ASC
             LIMIT ?
            """;

    private static final String LOAD_PENDING_MESSAGES_AFTER_SUMMARY_SQL = """
            SELECT id, conversation_id, user_id, role, content, thinking_content, thinking_duration, create_time, update_time
              FROM t_message
             WHERE conversation_id = ? AND user_id = ? AND id > ?
             ORDER BY create_time ASC
             LIMIT ?
            """;

    private static final String UPSERT_SUMMARY_SQL = """
            INSERT INTO t_conversation_summary
                (id, conversation_id, user_id, summary, message_count, last_summarized_message_id, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (conversation_id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                summary = EXCLUDED.summary,
                message_count = EXCLUDED.message_count,
                last_summarized_message_id = EXCLUDED.last_summarized_message_id,
                update_time = NOW()
            """;

    private final JdbcTemplate jdbcTemplate;
    private final LLMService llmService;
    private final RedissonClient redissonClient;
    private final ConversationMemoryProperties properties;

    @Override
    public Optional<String> loadSummary(String conversationId, String userId) {
        return loadSummaryRow(conversationId, userId).map(ConversationSummaryDO::getSummary);
    }

    /**
     * 检查用户消息数是否超过阈值，超过时获取分布式锁执行摘要压缩。
     */
    @Override
    public void compressIfNeeded(String conversationId, String userId) {
        // 统计当前会话的用户消息数，判断是否需要触发摘要压缩
        Long userMessageCount = jdbcTemplate.queryForObject(
                COUNT_USER_MESSAGES_SQL,
                Long.class,
                conversationId,
                userId
        );
        long count = userMessageCount == null ? 0 : userMessageCount;
        // 未达到阈值，跳过压缩
        if (count <= properties.getSummaryStartTurns()) {
            return;
        }

        // 获取分布式锁，防止多实例同时压缩同一会话
        RLock lock = redissonClient.getLock(LOCK_PREFIX + conversationId);
        boolean acquired;
        try {
            // tryLock 是非阻塞的：等待指定时间后立即返回，不会无限阻塞
            acquired = lock.tryLock(
                    properties.getLockWaitMillis(),
                    properties.getLockLeaseMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("获取对话摘要锁被中断，conversationId={}, userId={}", conversationId, userId);
            return;
        }
        // 未获取到锁，说明其他实例正在压缩，直接跳过
        if (!acquired) {
            return;
        }

        try {
            compressLocked(conversationId, userId);
        } finally {
            // 只释放当前线程持有的锁，避免误释放其他线程的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 在分布式锁内执行摘要压缩：加载已有摘要和待压缩消息，调用 LLM 生成新摘要后 upsert。
     */
    private void compressLocked(String conversationId, String userId) {
        // 1. 加载已有摘要记录（可能为空）
        Optional<ConversationSummaryDO> existing = loadSummaryRow(conversationId, userId);
        // 2. 加载待压缩的消息：有摘要时取 lastSummarizedMessageId 之后的消息，无摘要时取前 N 条
        List<ConversationMessageDO> pendingMessages = loadPendingMessages(conversationId, userId, existing.orElse(null));
        if (pendingMessages.isEmpty()) {
            return;
        }

        // 3. 将已有摘要和新消息填充到 Prompt 模板
        String prompt = renderPrompt(existing.map(ConversationSummaryDO::getSummary).orElse(""),
                formatMessages(pendingMessages));
        // 4. 调用 LLM 生成新的合并摘要
        String summary = llmService.chat(prompt);
        if (!StringUtils.hasText(summary)) {
            return;
        }

        // 5. 更新计数和游标，upsert 摘要记录
        int previousCount = existing.map(ConversationSummaryDO::getMessageCount).orElse(0);
        Integer messageCount = previousCount + pendingMessages.size();
        // 记录最后一条被压缩消息的 ID，下次从此之后开始增量压缩
        Long lastMessageId = pendingMessages.get(pendingMessages.size() - 1).getId();
        jdbcTemplate.update(
                UPSERT_SUMMARY_SQL,
                IdUtil.getSnowflakeNextId(),
                conversationId,
                userId,
                summary.trim(),
                messageCount,
                lastMessageId
        );
    }

    private Optional<ConversationSummaryDO> loadSummaryRow(String conversationId, String userId) {
        List<ConversationSummaryDO> summaries = jdbcTemplate.query(
                LOAD_SUMMARY_SQL,
                this::mapSummary,
                conversationId,
                userId
        );
        if (summaries == null || summaries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(summaries.get(0));
    }

    /**
     * 加载待压缩的消息：有摘要时取 lastSummarizedMessageId 之后的消息，无摘要时取前 N 条。
     */
    private List<ConversationMessageDO> loadPendingMessages(String conversationId, String userId,
                                                            ConversationSummaryDO existingSummary) {
        long limit = Math.max(1, properties.getSummaryBatchSize());
        // 已有摘要：从上次压缩游标之后开始加载增量消息
        if (existingSummary != null && existingSummary.getLastSummarizedMessageId() != null) {
            return jdbcTemplate.query(
                    LOAD_PENDING_MESSAGES_AFTER_SUMMARY_SQL,
                    this::mapMessage,
                    conversationId,
                    userId,
                    existingSummary.getLastSummarizedMessageId(),
                    limit
            );
        }
        // 无摘要：从最早的消息开始加载前 N 条
        return jdbcTemplate.query(
                LOAD_PENDING_MESSAGES_WITHOUT_SUMMARY_SQL,
                this::mapMessage,
                conversationId,
                userId,
                limit
        );
    }

    /**
     * 将已有摘要和新消息填充到摘要压缩 Prompt 模板中。
     */
    private String renderPrompt(String summary, String messages) {
        String template = loadTemplate();
        return template
                .replace("{{summary}}", StringUtils.hasText(summary) ? summary : "None")
                .replace("{{messages}}", messages);
    }

    /**
     * 加载摘要压缩 Prompt 模板，classpath 文件不存在或读取失败时使用默认模板兜底。
     */
    private String loadTemplate() {
        ClassPathResource resource = new ClassPathResource("rag/prompt/conversation-summary.st");
        // 文件不存在时使用硬编码的默认模板
        if (!resource.exists()) {
            return DEFAULT_TEMPLATE;
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            // 读取异常时降级到默认模板，不影响主流程
            log.warn("加载对话摘要模板失败，使用默认模板", ex);
            return DEFAULT_TEMPLATE;
        }
    }

    /** 将消息列表格式化为 [role] content 的文本格式，供 LLM 阅读。 */
    private String formatMessages(List<ConversationMessageDO> messages) {
        StringBuilder builder = new StringBuilder();
        for (ConversationMessageDO message : messages) {
            builder.append('[')
                    .append(message.getRole())
                    .append("] ")
                    .append(message.getContent());
            if (StringUtils.hasText(message.getThinkingContent())) {
                builder.append("\n[assistant_thinking] ")
                        .append(message.getThinkingContent());
            }
            builder.append("\n\n");
        }
        return builder.toString().trim();
    }

    /**
     * 将 ResultSet 映射为 ConversationSummaryDO，处理 nullable 字段的 wasNull 检测。
     */
    private ConversationSummaryDO mapSummary(ResultSet rs, int rowNum) throws SQLException {
        ConversationSummaryDO summary = new ConversationSummaryDO();
        summary.setId(rs.getLong("id"));
        summary.setConversationId(rs.getString("conversation_id"));
        summary.setUserId(rs.getString("user_id"));
        summary.setSummary(rs.getString("summary"));
        // getInt 返回基本类型，需通过 wasNull 判断数据库 NULL
        int messageCount = rs.getInt("message_count");
        summary.setMessageCount(rs.wasNull() ? null : messageCount);
        long lastMessageId = rs.getLong("last_summarized_message_id");
        summary.setLastSummarizedMessageId(rs.wasNull() ? null : lastMessageId);
        summary.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        summary.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        return summary;
    }

    /**
     * 将 ResultSet 映射为 ConversationMessageDO，处理 nullable 的 thinkingDuration 字段。
     */
    private ConversationMessageDO mapMessage(ResultSet rs, int rowNum) throws SQLException {
        ConversationMessageDO message = new ConversationMessageDO();
        message.setId(rs.getLong("id"));
        message.setConversationId(rs.getString("conversation_id"));
        message.setUserId(rs.getString("user_id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        message.setThinkingContent(rs.getString("thinking_content"));
        // thinking_duration 可能为 NULL，需通过 wasNull 检测
        int duration = rs.getInt("thinking_duration");
        message.setThinkingDuration(rs.wasNull() ? null : duration);
        message.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        message.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        return message;
    }

    /** JDBC Timestamp 转 LocalDateTime，null 安全。 */
    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
