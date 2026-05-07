package edu.cqupt.devbrain.rag.core.memory;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultConversationMemoryServiceTest {

    private final ConversationMemoryStore store = mock(ConversationMemoryStore.class);
    private final ConversationMemorySummaryService summaryService = mock(ConversationMemorySummaryService.class);
    private final ConversationMemoryProperties properties = new ConversationMemoryProperties();
    private final Executor directExecutor = Runnable::run;
    private final DefaultConversationMemoryService memoryService =
            new DefaultConversationMemoryService(store, summaryService, properties, directExecutor, directExecutor);

    @Test
    void loadShouldPrependSummaryToRecentHistory() {
        properties.setHistoryKeepTurns(2);
        when(summaryService.loadSummary("conv-1", "user-1")).thenReturn(Optional.of("User asked about RAG memory."));
        when(store.loadRecentHistory("conv-1", "user-1", 2)).thenReturn(List.of(
                ChatMessage.user("How does memory work?"),
                ChatMessage.assistant("Recent messages are loaded with a summary.", null, null)
        ));

        List<ChatMessage> result = memoryService.load("conv-1", "user-1");

        assertEquals(3, result.size());
        assertEquals(ChatMessage.Role.SYSTEM, result.get(0).getRole());
        assertEquals("User asked about RAG memory.", result.get(0).getContent());
        assertEquals(ChatMessage.Role.USER, result.get(1).getRole());
        assertEquals(ChatMessage.Role.ASSISTANT, result.get(2).getRole());
        verify(store).loadRecentHistory("conv-1", "user-1", 2);
    }

    @Test
    void appendShouldSaveMessageAndTriggerSummaryCompressionAsynchronously() {
        ChatMessage message = ChatMessage.assistant("answer", "thinking", 3);
        when(store.saveMessage("conv-1", "user-1", "assistant", "answer", "thinking", 3)).thenReturn("101");

        String messageId = memoryService.append("conv-1", "user-1", message);

        assertEquals("101", messageId);
        verify(summaryService).compressIfNeeded("conv-1", "user-1");
    }
}
