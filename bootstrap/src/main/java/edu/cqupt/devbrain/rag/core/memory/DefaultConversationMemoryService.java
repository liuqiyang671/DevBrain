package edu.cqupt.devbrain.rag.core.memory;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * 默认对话记忆服务实现。
 */
@Slf4j
@Service
public class DefaultConversationMemoryService implements ConversationMemoryService {

    private final ConversationMemoryStore store;
    private final ConversationMemorySummaryService summaryService;
    private final ConversationMemoryProperties properties;
    private final Executor memoryLoadExecutor;
    private final Executor memorySummaryExecutor;

    public DefaultConversationMemoryService(ConversationMemoryStore store,
                                            ConversationMemorySummaryService summaryService,
                                            ConversationMemoryProperties properties,
                                            @Qualifier("memoryLoadExecutor") Executor memoryLoadExecutor,
                                            @Qualifier("memorySummaryExecutor") Executor memorySummaryExecutor) {
        this.store = store;
        this.summaryService = summaryService;
        this.properties = properties;
        this.memoryLoadExecutor = memoryLoadExecutor;
        this.memorySummaryExecutor = memorySummaryExecutor;
    }

    @Override
    public List<ChatMessage> load(String conversationId, String userId) {
        validateIdentity(conversationId, userId);
        int limit = Math.max(1, properties.getHistoryKeepTurns());

        CompletableFuture<List<ChatMessage>> historyFuture = CompletableFuture.supplyAsync(
                () -> store.loadRecentHistory(conversationId, userId, limit),
                memoryLoadExecutor
        );
        CompletableFuture<String> summaryFuture = CompletableFuture.supplyAsync(
                () -> summaryService.loadSummary(conversationId, userId).orElse(null),
                memoryLoadExecutor
        );

        try {
            List<ChatMessage> history = historyFuture.join();
            String summary = summaryFuture.join();
            List<ChatMessage> messages = new ArrayList<>();
            if (StringUtils.hasText(summary)) {
                messages.add(ChatMessage.system(summary));
            }
            if (history != null && !history.isEmpty()) {
                messages.addAll(history);
            }
            return messages;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new ServiceException("加载对话记忆失败", cause, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public String append(String conversationId, String userId, ChatMessage message) {
        validateIdentity(conversationId, userId);
        validateMessage(message);
        String messageId = store.saveMessage(
                conversationId,
                userId,
                message.getRole().name().toLowerCase(Locale.ROOT),
                message.getContent(),
                message.getThinkingContent(),
                message.getThinkingDuration()
        );

        CompletableFuture.runAsync(() -> summaryService.compressIfNeeded(conversationId, userId), memorySummaryExecutor)
                .exceptionally(ex -> {
                    log.warn("异步压缩对话摘要失败，conversationId={}, userId={}", conversationId, userId, ex);
                    return null;
                });
        return messageId;
    }

    private void validateIdentity(String conversationId, String userId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new ClientException("conversationId 不能为空");
        }
        if (!StringUtils.hasText(userId)) {
            throw new ClientException("userId 不能为空");
        }
    }

    private void validateMessage(ChatMessage message) {
        if (message == null) {
            throw new ClientException("对话消息不能为空");
        }
        if (message.getRole() == null) {
            throw new ClientException("对话消息角色不能为空");
        }
        if (!StringUtils.hasText(message.getContent())) {
            throw new ClientException("对话消息正文不能为空");
        }
    }
}
