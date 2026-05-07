package edu.cqupt.devbrain.rag.core.memory;

import cn.hutool.core.util.IdUtil;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 基于 JdbcTemplate 的对话消息存储实现。
 */
@Service
@RequiredArgsConstructor
public class JdbcConversationMemoryStore implements ConversationMemoryStore {

    private static final String ENSURE_CONVERSATION_SQL = """
            INSERT INTO t_conversation
                (id, conversation_id, user_id, last_time, create_time, update_time)
            VALUES (?, ?, ?, NOW(), NOW(), NOW())
            ON CONFLICT (conversation_id) DO UPDATE SET
                last_time = NOW(),
                update_time = NOW()
            WHERE t_conversation.user_id = EXCLUDED.user_id
            """;

    private static final String INSERT_MESSAGE_SQL = """
            INSERT INTO t_message
                (id, conversation_id, user_id, role, content, thinking_content, thinking_duration, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;

    private static final String LOAD_RECENT_HISTORY_SQL = """
            SELECT id, conversation_id, user_id, role, content, thinking_content, thinking_duration, create_time, update_time
              FROM t_message
             WHERE conversation_id = ? AND user_id = ?
             ORDER BY create_time DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ChatMessage> loadRecentHistory(String conversationId, String userId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<ConversationMessageDO> rows = jdbcTemplate.query(
                LOAD_RECENT_HISTORY_SQL,
                this::mapMessage,
                conversationId,
                userId,
                limit
        );
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ConversationMessageDO> chronological = new ArrayList<>(rows);
        Collections.reverse(chronological);
        return chronological.stream().map(this::toChatMessage).toList();
    }

    @Override
    public String saveMessage(String conversationId, String userId, String role, String content,
                              String thinkingContent, Integer thinkingDuration) {
        Long messageId = IdUtil.getSnowflakeNextId();
        int conversationRows = jdbcTemplate.update(
                ENSURE_CONVERSATION_SQL,
                IdUtil.getSnowflakeNextId(),
                conversationId,
                userId
        );
        if (conversationRows == 0) {
            throw new ClientException("会话不属于当前用户");
        }
        jdbcTemplate.update(
                INSERT_MESSAGE_SQL,
                messageId,
                conversationId,
                userId,
                normalizeRole(role),
                content,
                thinkingContent,
                thinkingDuration
        );
        return String.valueOf(messageId);
    }

    private ConversationMessageDO mapMessage(ResultSet rs, int rowNum) throws SQLException {
        ConversationMessageDO message = new ConversationMessageDO();
        message.setId(rs.getLong("id"));
        message.setConversationId(rs.getString("conversation_id"));
        message.setUserId(rs.getString("user_id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        message.setThinkingContent(rs.getString("thinking_content"));
        int duration = rs.getInt("thinking_duration");
        message.setThinkingDuration(rs.wasNull() ? null : duration);
        message.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        message.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        return message;
    }

    private ChatMessage toChatMessage(ConversationMessageDO row) {
        ChatMessage.Role role = ChatMessage.Role.fromString(row.getRole());
        if (role == ChatMessage.Role.ASSISTANT) {
            return ChatMessage.assistant(row.getContent(), row.getThinkingContent(), row.getThinkingDuration());
        }
        if (role == ChatMessage.Role.SYSTEM) {
            return ChatMessage.system(row.getContent());
        }
        return ChatMessage.user(row.getContent());
    }

    private String normalizeRole(String role) {
        return ChatMessage.Role.fromString(role).name().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
