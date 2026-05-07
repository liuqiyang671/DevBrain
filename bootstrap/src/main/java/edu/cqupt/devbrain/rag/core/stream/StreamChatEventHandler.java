package edu.cqupt.devbrain.rag.core.stream;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import edu.cqupt.devbrain.rag.core.memory.ConversationMemoryService;
import edu.cqupt.devbrain.rag.enums.SSEEventType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * LLM 流式 token 到 SSE 事件的转换处理器。
 */
public class StreamChatEventHandler implements StreamCallback {

    private static final String ASSISTANT_USER_ID = "__assistant__";

    private final String conversationId;
    private final String userId;
    private final String taskId;
    private final ConversationMemoryService memoryService;
    private final StreamTaskManager streamTaskManager;
    private final SseEmitterSender sender;
    private final StringBuilder answerBuffer = new StringBuilder();
    private final StringBuilder thinkingBuffer = new StringBuilder();
    private final int messageChunkSize;
    private Long thinkingStartMs;

    public StreamChatEventHandler(SseEmitter emitter,
                                  String conversationId,
                                  String taskId,
                                  ConversationMemoryService memoryService,
                                  AIModelProperties aiModelProperties,
                                  StreamTaskManager streamTaskManager) {
        this(emitter, conversationId, taskId, ASSISTANT_USER_ID, memoryService, aiModelProperties, streamTaskManager);
    }

    public StreamChatEventHandler(SseEmitter emitter,
                                  String conversationId,
                                  String taskId,
                                  String userId,
                                  ConversationMemoryService memoryService,
                                  AIModelProperties aiModelProperties,
                                  StreamTaskManager streamTaskManager) {
        this.conversationId = conversationId;
        this.userId = userId == null || userId.isBlank() ? ASSISTANT_USER_ID : userId;
        this.taskId = taskId;
        this.memoryService = memoryService;
        this.streamTaskManager = streamTaskManager;
        this.sender = new SseEmitterSender(emitter);
        this.messageChunkSize = normalizeChunkSize(aiModelProperties);
        this.sender.sendEvent(SSEEventType.META, new MetaPayload(conversationId, taskId));
        this.streamTaskManager.register(taskId, this::cancel);
    }

    @Override
    public void onContent(String chunk) {
        if (chunk == null || chunk.isEmpty() || sender.isClosed()) {
            return;
        }
        answerBuffer.append(chunk);
        sendChunks("response", chunk);
    }

    @Override
    public void onThinking(String chunk) {
        if (chunk == null || chunk.isEmpty() || sender.isClosed()) {
            return;
        }
        if (thinkingStartMs == null) {
            thinkingStartMs = System.currentTimeMillis();
        }
        thinkingBuffer.append(chunk);
        sendChunks("think", chunk);
    }

    @Override
    public void onComplete() {
        if (sender.isClosed()) {
            return;
        }
        try {
            String answer = answerBuffer.toString();
            String thinking = thinkingBuffer.toString();
            Integer thinkingDuration = calculateThinkingDuration();
            ChatMessage message = ChatMessage.assistant(answer, thinking, thinkingDuration);
            String messageId = memoryService.append(conversationId, userId, message);
            sender.sendEvent(SSEEventType.FINISH, new CompletionPayload(messageId, null));
            sender.sendEvent(SSEEventType.DONE, "[DONE]");
            streamTaskManager.unregister(taskId);
            sender.complete();
        } catch (Throwable ex) {
            onError(ex);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        try {
            sender.sendEvent(SSEEventType.ERROR, new ErrorPayload(errorMessage(throwable)));
        } finally {
            streamTaskManager.unregister(taskId);
            sender.fail(throwable);
        }
    }

    public void cancel() {
        if (sender.isClosed()) {
            return;
        }
        try {
            persistPartialAssistant();
            sender.sendEvent(SSEEventType.CANCEL, new ErrorPayload("cancelled"));
            streamTaskManager.unregister(taskId);
            sender.complete();
        } catch (Throwable ex) {
            onError(ex);
        }
    }

    private void sendChunks(String type, String chunk) {
        for (String part : splitByCodePoint(chunk)) {
            sender.sendEvent(SSEEventType.MESSAGE, new MessageDelta(type, part));
        }
    }

    private java.util.List<String> splitByCodePoint(String value) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int maxCodePoints = Math.max(1, messageChunkSize);
        int offset = 0;
        while (offset < value.length()) {
            int remainingCodePoints = value.codePointCount(offset, value.length());
            int codePoints = Math.min(maxCodePoints, remainingCodePoints);
            int end = value.offsetByCodePoints(offset, codePoints);
            result.add(value.substring(offset, end));
            offset = end;
        }
        return result;
    }

    private Integer calculateThinkingDuration() {
        if (thinkingStartMs == null || thinkingBuffer.isEmpty()) {
            return null;
        }
        return (int) Math.max(0, (System.currentTimeMillis() - thinkingStartMs) / 1000);
    }

    private void persistPartialAssistant() {
        String answer = answerBuffer.toString();
        String thinking = thinkingBuffer.toString();
        if (answer.isBlank()) {
            return;
        }
        ChatMessage message = ChatMessage.assistant(answer, thinking, calculateThinkingDuration());
        memoryService.append(conversationId, userId, message);
    }

    private int normalizeChunkSize(AIModelProperties properties) {
        if (properties == null || properties.getChat() == null
                || properties.getChat().getMessageChunkSize() <= 0) {
            return 256;
        }
        return properties.getChat().getMessageChunkSize();
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "unknown error";
        }
        return throwable.getMessage();
    }
}
