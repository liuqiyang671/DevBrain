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
    private static final String EMPTY_ANSWER_FALLBACK = "抱歉，这次没有生成有效回答，请再试一次。";

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
        // 从配置读取 token 分片大小，控制 SSE 推送粒度
        this.messageChunkSize = normalizeChunkSize(aiModelProperties);
        // 构造完成后立即发送 meta 事件，前端据此关联会话
        this.sender.sendEvent(SSEEventType.META, new MetaPayload(conversationId, taskId));
        onTrace("stream.meta", "SSE 连接已建立，已分配会话和任务 ID");
        // 注册取消回调，用户调用 stop 接口时会触发 cancel()
        this.streamTaskManager.register(taskId, this::cancel);
    }

    /** 接收 LLM 回答 token，累积到缓冲区并通过 SSE 推送给前端。 */
    @Override
    public void onContent(String chunk) {
        if (chunk == null || chunk.isEmpty() || sender.isClosed()) {
            return;
        }
        answerBuffer.append(chunk);
        sendChunks("response", chunk);
    }

    /** 接收 LLM 思考过程 token，累积到思考缓冲区。 */
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

    /** 发送后端处理阶段追踪事件，前端只打印到控制台。 */
    @Override
    public void onTrace(String stage, String message) {
        if (sender.isClosed()) {
            return;
        }
        sender.sendEvent(SSEEventType.TRACE, new TracePayload(
                stage,
                message,
                conversationId,
                taskId,
                System.currentTimeMillis()
        ));
    }

    /** LLM 流式回答完成，持久化助手消息并发送 finish/done 事件。 */
    @Override
    public void onComplete() {
        if (sender.isClosed()) {
            return;
        }
        try {
            onTrace("stream.persist", "模型输出完成，正在持久化助手消息");
            // 从缓冲区取出完整的回答和思考内容
            String answer = answerBuffer.toString();
            String thinking = thinkingBuffer.toString();
            Integer thinkingDuration = calculateThinkingDuration();
            if (answer.isBlank()) {
                answer = EMPTY_ANSWER_FALLBACK;
                answerBuffer.append(answer);
                onTrace("stream.empty-content", "模型流结束但没有返回正文，已下发兜底回答");
                sendChunks("response", answer);
            }
            // 将助手消息持久化到数据库
            ChatMessage message = ChatMessage.assistant(answer, thinking, thinkingDuration);
            String messageId = memoryService.append(conversationId, userId, message);
            // 发送 finish 事件（携带 messageId）和 done 标记
            onTrace("stream.done", "助手消息已保存，SSE 流即将结束");
            sender.sendEvent(SSEEventType.FINISH, new CompletionPayload(messageId, null));
            sender.sendEvent(SSEEventType.DONE, "[DONE]");
            // 注销任务并关闭 SSE 连接
            streamTaskManager.unregister(taskId);
            sender.complete();
        } catch (Throwable ex) {
            onError(ex);
        }
    }

    /** LLM 流式回答异常，发送 error 事件并关闭 SSE 连接。 */
    @Override
    public void onError(Throwable throwable) {
        try {
            onTrace("stream.error", errorMessage(throwable));
            sender.sendEvent(SSEEventType.ERROR, new ErrorPayload(errorMessage(throwable)));
        } finally {
            streamTaskManager.unregister(taskId);
            sender.fail(throwable);
        }
    }

    /**
     * 取消当前流式任务，持久化已生成的部分回答并发送 cancel 事件。
     */
    public void cancel() {
        if (sender.isClosed()) {
            return;
        }
        try {
            onTrace("stream.cancel", "收到取消请求，正在保存已生成的部分回答");
            // 持久化已生成的部分回答，避免取消后丢失内容
            persistPartialAssistant();
            // 发送 cancel 事件通知前端
            sender.sendEvent(SSEEventType.CANCEL, new ErrorPayload("cancelled"));
            // 注销任务并关闭 SSE 连接
            streamTaskManager.unregister(taskId);
            sender.complete();
        } catch (Throwable ex) {
            onError(ex);
        }
    }

    /** 将 token 按 codePoint 粒度拆分后逐个发送 SSE message 事件。 */
    private void sendChunks(String type, String chunk) {
        for (String part : splitByCodePoint(chunk)) {
            sender.sendEvent(SSEEventType.MESSAGE, new MessageDelta(type, part));
        }
    }

    /**
     * 按 Unicode codePoint 粒度拆分文本，避免在 surrogate pair 中间截断。
     */
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

    /** 计算思考过程耗时（秒），从首个思考 token 到完成的时间差。 */
    private Integer calculateThinkingDuration() {
        if (thinkingStartMs == null || thinkingBuffer.isEmpty()) {
            return null;
        }
        return (int) Math.max(0, (System.currentTimeMillis() - thinkingStartMs) / 1000);
    }

    /** 取消时持久化已生成的部分助手消息。 */
    private void persistPartialAssistant() {
        String answer = answerBuffer.toString();
        String thinking = thinkingBuffer.toString();
        if (answer.isBlank()) {
            return;
        }
        ChatMessage message = ChatMessage.assistant(answer, thinking, calculateThinkingDuration());
        memoryService.append(conversationId, userId, message);
    }

    /**
     * 读取配置的 token 分片大小，未配置或无效时默认 256。
     */
    private int normalizeChunkSize(AIModelProperties properties) {
        if (properties == null || properties.getChat() == null
                || properties.getChat().getMessageChunkSize() <= 0) {
            return 256;
        }
        return properties.getChat().getMessageChunkSize();
    }

    /** 提取异常消息，null 安全。 */
    private String errorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "unknown error";
        }
        return throwable.getMessage();
    }
}
