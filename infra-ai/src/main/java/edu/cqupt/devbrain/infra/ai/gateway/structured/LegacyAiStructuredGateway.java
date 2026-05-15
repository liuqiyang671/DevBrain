package edu.cqupt.devbrain.infra.ai.gateway.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static edu.cqupt.devbrain.framework.errorcode.BaseErrorCode.SERVICE_ERROR;

/**
 * 基于现有 LLMService 的结构化输出网关。
 */
public class LegacyAiStructuredGateway implements AiStructuredGateway {

    private final LLMService llmService;
    private final AiJsonOutputParser parser;
    private final AIModelProperties properties;
    private final List<AiCallObserver> observers;

    public LegacyAiStructuredGateway(LLMService llmService,
                                     ObjectMapper objectMapper,
                                     AIModelProperties properties,
                                     List<AiCallObserver> observers) {
        this.llmService = llmService;
        this.parser = new AiJsonOutputParser(objectMapper);
        this.properties = properties == null ? new AIModelProperties() : properties;
        this.observers = observers == null ? List.of() : List.copyOf(observers);
    }

    @Override
    public <T> AiStructuredResponse<T> structured(AiStructuredRequest<T> request) {
        if (request == null || request.getResponseType() == null) {
            throw new ServiceException("结构化输出 responseType 不能为空", SERVICE_ERROR);
        }
        String callId = UUID.randomUUID().toString();
        ChatRequest chatRequest = toChatRequest(request);
        String prompt = promptSummary(chatRequest.getMessages());
        notifyStart(record(callId, request, 0L, "running", null, prompt, null));
        long start = System.nanoTime();
        String raw = null;
        try {
            raw = llmService.chat(chatRequest);
            AiJsonOutputParser.ParseResult<T> parsed = parser.parseWithWarnings(raw, request.getResponseType());
            long durationMs = elapsedMillis(start);
            notifyComplete(record(callId, request, durationMs, "succeeded", null, prompt, raw));
            return AiStructuredResponse.<T>builder()
                    .value(parsed.value())
                    .rawContent(raw)
                    .durationMs(durationMs)
                    .callId(callId)
                    .parseWarnings(parsed.warnings())
                    .build();
        } catch (RuntimeException ex) {
            long durationMs = elapsedMillis(start);
            notifyComplete(record(callId, request, durationMs, "failed", ex.getMessage(), prompt, raw));
            throw ex;
        }
    }

    private <T> ChatRequest toChatRequest(AiStructuredRequest<T> request) {
        return ChatRequest.builder()
                .messages(withStructuredSystemPrompt(request.getMessages(), request.getSchema()))
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .thinking(false)
                .responseFormat(structuredOutputEnabled() ? ChatRequest.ResponseFormat.jsonObject() : null)
                .timeoutMillis(request.getTimeoutMillis())
                .build();
    }

    private List<ChatMessage> withStructuredSystemPrompt(List<ChatMessage> messages, Map<String, Object> schema) {
        List<ChatMessage> result = new ArrayList<>();
        String schemaText = schema == null || schema.isEmpty() ? "{}" : schema.toString();
        result.add(ChatMessage.system("""
                你必须只输出一个 JSON 对象，不要输出 Markdown、解释或多余文本。
                输出必须符合业务约束和字段语义，不能编造没有来源的价格、库存或优惠。
                响应 Schema/契约：%s
                """.formatted(schemaText)));
        if (messages != null) {
            result.addAll(messages);
        }
        return result;
    }

    private boolean structuredOutputEnabled() {
        return properties.getChat() == null || properties.getChat().isStructuredOutputEnabled();
    }

    private <T> AiCallRecord record(String callId, AiStructuredRequest<T> request, long durationMs,
                                    String status, String errorMessage, String prompt, String response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(request.getMetadata() == null ? Map.of() : request.getMetadata());
        metadata.put("fallbackAllowed", request.isFallbackAllowed());
        metadata.put("responseType", request.getResponseType().getSimpleName());
        metadata.put("timeoutMillis", request.getTimeoutMillis());
        return new AiCallRecord(
                callId,
                request.getRunId(),
                request.getStepId(),
                StringUtils.hasText(request.getBusinessScene()) ? request.getBusinessScene() : "ai.structured",
                null,
                null,
                false,
                request.getTemperature(),
                request.getMaxTokens(),
                null,
                null,
                durationMs,
                status,
                errorMessage,
                prompt,
                response,
                metadata
        );
    }

    private void notifyStart(AiCallRecord record) {
        for (AiCallObserver observer : observers) {
            observer.onStart(record);
        }
    }

    private void notifyComplete(AiCallRecord record) {
        for (AiCallObserver observer : observers) {
            observer.onComplete(record);
        }
    }

    private String promptSummary(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .map(message -> {
                    String role = message.getRole() == null ? "user" : message.getRole().name().toLowerCase();
                    return role + ": " + (message.getContent() == null ? "" : message.getContent());
                })
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private long elapsedMillis(long start) {
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }
}
