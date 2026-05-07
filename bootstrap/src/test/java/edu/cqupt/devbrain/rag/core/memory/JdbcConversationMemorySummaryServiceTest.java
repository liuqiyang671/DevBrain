package edu.cqupt.devbrain.rag.core.memory;

import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcConversationMemorySummaryServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final LLMService llmService = mock(LLMService.class);
    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final ConversationMemoryProperties properties = new ConversationMemoryProperties();
    private final JdbcConversationMemorySummaryService summaryService =
            new JdbcConversationMemorySummaryService(jdbcTemplate, llmService, redissonClient, properties);

    @Test
    void compressIfNeededShouldSkipWhenUserMessageCountDoesNotReachThreshold() {
        properties.setSummaryStartTurns(10);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("conv-1"), eq("user-1")))
                .thenReturn(3L);

        summaryService.compressIfNeeded("conv-1", "user-1");

        verify(redissonClient, never()).getLock(anyString());
        verify(llmService, never()).chat(anyString());
    }

    @Test
    void compressIfNeededShouldSkipWhenUserMessageCountEqualsThreshold() {
        properties.setSummaryStartTurns(10);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("conv-1"), eq("user-1")))
                .thenReturn(10L);

        summaryService.compressIfNeeded("conv-1", "user-1");

        verify(redissonClient, never()).getLock(anyString());
        verify(llmService, never()).chat(anyString());
    }

    @Test
    void compressIfNeededShouldLockSummarizePendingMessagesAndUpsertSummary() throws Exception {
        properties.setSummaryStartTurns(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("conv-1"), eq("user-1")))
                .thenReturn(2L);
        when(redissonClient.getLock("devbrain:conversation:summary:lock:conv-1")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("conv-1"), eq("user-1")))
                .thenReturn(List.of(summary()));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("conv-1"), eq("user-1"), eq(100L), eq(100L)))
                .thenReturn(List.of(
                        message(101L, "user", "What is RAG?"),
                        message(102L, "assistant", "Retrieval augmented generation.")
                ));
        when(llmService.chat(anyString())).thenReturn("Updated concise summary");

        summaryService.compressIfNeeded("conv-1", "user-1");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("Previous summary"));
        assertTrue(promptCaptor.getValue().contains("What is RAG?"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("INSERT INTO t_conversation_summary"),
                any(), eq("conv-1"), eq("user-1"), eq("Updated concise summary"), eq(5), eq(102L));
        verify(lock).unlock();
    }

    private ConversationSummaryDO summary() {
        ConversationSummaryDO summary = new ConversationSummaryDO();
        summary.setId(1L);
        summary.setConversationId("conv-1");
        summary.setUserId("user-1");
        summary.setSummary("Previous summary");
        summary.setMessageCount(3);
        summary.setLastSummarizedMessageId(100L);
        summary.setCreateTime(LocalDateTime.now());
        summary.setUpdateTime(LocalDateTime.now());
        return summary;
    }

    private ConversationMessageDO message(Long id, String role, String content) {
        ConversationMessageDO message = new ConversationMessageDO();
        message.setId(id);
        message.setConversationId("conv-1");
        message.setUserId("user-1");
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        return message;
    }
}
