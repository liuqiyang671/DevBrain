package edu.cqupt.devbrain.rag.core.memory;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcConversationMemoryStoreTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcConversationMemoryStore store = new JdbcConversationMemoryStore(jdbcTemplate);

    @Test
    void loadRecentHistoryShouldQueryDescendingAndReturnChronologicalMessages() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), any(RowMapper.class), eq("conv-1"), eq("user-1"), eq(2)))
                .thenReturn(List.of(
                        message(102L, "assistant", "new answer", "thinking", 4),
                        message(101L, "user", "old question", null, null)
                ));

        List<ChatMessage> messages = store.loadRecentHistory("conv-1", "user-1", 2);

        assertTrue(sqlCaptor.getValue().contains("FROM t_message"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY create_time DESC"));
        assertTrue(sqlCaptor.getValue().contains("LIMIT ?"));
        assertEquals(ChatMessage.Role.USER, messages.get(0).getRole());
        assertEquals("old question", messages.get(0).getContent());
        assertEquals(ChatMessage.Role.ASSISTANT, messages.get(1).getRole());
        assertEquals("thinking", messages.get(1).getThinkingContent());
        assertEquals(4, messages.get(1).getThinkingDuration());
    }

    private ConversationMessageDO message(Long id, String role, String content,
                                          String thinkingContent, Integer thinkingDuration) {
        ConversationMessageDO message = new ConversationMessageDO();
        message.setId(id);
        message.setConversationId("conv-1");
        message.setUserId("user-1");
        message.setRole(role);
        message.setContent(content);
        message.setThinkingContent(thinkingContent);
        message.setThinkingDuration(thinkingDuration);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        return message;
    }
}
