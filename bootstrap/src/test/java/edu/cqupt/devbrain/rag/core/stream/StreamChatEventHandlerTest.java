package edu.cqupt.devbrain.rag.core.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import edu.cqupt.devbrain.rag.core.memory.ConversationMemoryService;
import edu.cqupt.devbrain.rag.enums.SSEEventType;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamChatEventHandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StubConversationMemoryService memoryService = new StubConversationMemoryService();
    private final AIModelProperties properties = new AIModelProperties();
    private final StreamTaskManager taskManager = new StreamTaskManager();

    @Test
    void constructorShouldSendMetaAndRegisterTask() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();

        new StreamChatEventHandler(emitter, "conv-1", "task-1", memoryService, properties, taskManager);

        assertEquals("meta", emitter.events.get(0).name());
        JsonNode payload = OBJECT_MAPPER.readTree(emitter.events.get(0).data());
        assertEquals("conv-1", payload.get("conversationId").asText());
        assertEquals("task-1", payload.get("taskId").asText());
        assertTrue(taskManager.cancel("task-1"));
    }

    @Test
    void onContentShouldSplitByUnicodeCodePointWithoutBreakingEmoji() throws Exception {
        properties.getChat().setMessageChunkSize(2);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        StreamChatEventHandler handler =
                new StreamChatEventHandler(emitter, "conv-1", "task-1", memoryService, properties, taskManager);

        handler.onContent("A😊B");

        List<CapturedEvent> messageEvents = eventsOf(emitter, SSEEventType.MESSAGE);
        assertEquals(2, messageEvents.size());
        assertEquals("A😊", OBJECT_MAPPER.readTree(messageEvents.get(0).data()).get("content").asText());
        assertEquals("B", OBJECT_MAPPER.readTree(messageEvents.get(1).data()).get("content").asText());
    }

    @Test
    void onThinkingAndCompleteShouldPersistAssistantAndSendFinishDone() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        StreamChatEventHandler handler =
                new StreamChatEventHandler(emitter, "conv-1", "task-1", memoryService, properties, taskManager);

        handler.onThinking("分析中");
        handler.onContent("答案");
        handler.onComplete();

        assertEquals(1, memoryService.appendedMessages.size());
        ChatMessage message = memoryService.appendedMessages.get(0);
        assertEquals(ChatMessage.Role.ASSISTANT, message.getRole());
        assertEquals("答案", message.getContent());
        assertEquals("分析中", message.getThinkingContent());
        assertTrue(message.getThinkingDuration() >= 0);

        List<CapturedEvent> finishEvents = eventsOf(emitter, SSEEventType.FINISH);
        assertEquals(1, finishEvents.size());
        JsonNode finish = OBJECT_MAPPER.readTree(finishEvents.get(0).data());
        assertEquals("msg-1", finish.get("messageId").asText());
        assertEquals("done", emitter.events.get(emitter.events.size() - 1).name());
        assertEquals("[DONE]", emitter.events.get(emitter.events.size() - 1).data());
        assertTrue(emitter.completed);
        assertFalse(taskManager.cancel("task-1"));
    }

    @Test
    void onCompleteWithThinkingOnlyShouldSendFallbackAndPersistAssistant() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        StreamChatEventHandler handler =
                new StreamChatEventHandler(emitter, "conv-1", "task-1", memoryService, properties, taskManager);

        handler.onThinking("正在分析");
        handler.onComplete();

        String fallbackResponse = null;
        for (CapturedEvent event : eventsOf(emitter, SSEEventType.MESSAGE)) {
            JsonNode payload = OBJECT_MAPPER.readTree(event.data());
            if ("response".equals(payload.get("type").asText())) {
                fallbackResponse = payload.get("content").asText();
            }
        }
        assertTrue(fallbackResponse != null && fallbackResponse.contains("没有生成有效回答"));

        assertEquals(1, memoryService.appendedMessages.size());
        ChatMessage message = memoryService.appendedMessages.get(0);
        assertEquals(ChatMessage.Role.ASSISTANT, message.getRole());
        assertEquals(fallbackResponse, message.getContent());
        assertEquals("正在分析", message.getThinkingContent());
        assertEquals(0, eventsOf(emitter, SSEEventType.ERROR).size());
        assertTrue(emitter.completed);
        assertFalse(taskManager.cancel("task-1"));
    }

    @Test
    void onErrorShouldSendErrorAndComplete() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        StreamChatEventHandler handler =
                new StreamChatEventHandler(emitter, "conv-1", "task-1", memoryService, properties, taskManager);

        handler.onError(new IllegalStateException("模型失败"));

        List<CapturedEvent> errorEvents = eventsOf(emitter, SSEEventType.ERROR);
        assertEquals(1, errorEvents.size());
        assertTrue(errorEvents.get(0).data().contains("模型失败"));
        assertTrue(emitter.completed);
        assertFalse(emitter.failed);
        assertFalse(taskManager.cancel("task-1"));
    }

    @Test
    void cancelShouldSendCancelAndComplete() {
        CapturingSseEmitter emitter = new CapturingSseEmitter();

        new StreamChatEventHandler(emitter, "conv-1", "task-1", memoryService, properties, taskManager);
        boolean cancelled = taskManager.cancel("task-1");

        assertTrue(cancelled);
        assertEquals("cancel", emitter.events.get(emitter.events.size() - 1).name());
        assertTrue(emitter.completed);
    }

    private List<CapturedEvent> eventsOf(CapturingSseEmitter emitter, SSEEventType type) {
        return emitter.events.stream()
                .filter(event -> type.getValue().equals(event.name()))
                .toList();
    }

    private static final class CapturingSseEmitter extends SseEmitter {

        private final List<CapturedEvent> events = new ArrayList<>();
        private boolean completed;
        private boolean failed;

        @Override
        public synchronized void send(SseEventBuilder builder) {
            String raw = builder.build().stream()
                    .map(data -> String.valueOf(data.getData()))
                    .reduce("", (left, right) -> left + right);
            String name = lineValue(raw, "event:");
            String data = lineValue(raw, "data:");
            events.add(new CapturedEvent(name, data));
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(Throwable ex) {
            failed = true;
        }

        private String lineValue(String raw, String prefix) {
            for (String line : raw.split("\\R")) {
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).strip();
                }
            }
            return "";
        }
    }

    private record CapturedEvent(String name, String data) {
    }

    private static final class StubConversationMemoryService implements ConversationMemoryService {

        private final List<ChatMessage> appendedMessages = new ArrayList<>();

        @Override
        public List<ChatMessage> load(String conversationId, String userId) {
            return List.of();
        }

        @Override
        public String append(String conversationId, String userId, ChatMessage message) {
            appendedMessages.add(message);
            return "msg-" + appendedMessages.size();
        }
    }
}
