package edu.cqupt.devbrain.infra.ai.gateway.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.errorcode.BaseErrorCode;
import edu.cqupt.devbrain.framework.exception.ServiceException;
import edu.cqupt.devbrain.infra.ai.gateway.chat.AiChatGateway;
import edu.cqupt.devbrain.infra.ai.gateway.chat.AiChatRequest;
import edu.cqupt.devbrain.infra.ai.gateway.chat.AiChatResponse;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiJsonOutputParser;

import java.util.List;

/**
 * 基于对话网关的结构化抽取实现。
 * 通过构造prompt让AI返回JSON，然后解析为目标类型。
 */
public class LegacyAiStructuredExtractor implements AiStructuredExtractor {

    private static final int RAW_OUTPUT_LIMIT = 200;

    private final AiChatGateway chatGateway;
    private final AiJsonOutputParser parser;

    public LegacyAiStructuredExtractor(AiChatGateway chatGateway) {
        this(chatGateway, new ObjectMapper());
    }

    public LegacyAiStructuredExtractor(AiChatGateway chatGateway, ObjectMapper objectMapper) {
        this.chatGateway = chatGateway;
        this.parser = new AiJsonOutputParser(objectMapper);
    }

    @Override
    public <T> T extract(String prompt, String sourceText, Class<T> targetType) {
        AiChatResponse response = chatGateway.chat(AiChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt + "\n\n原文：\n" + sourceText)))
                .temperature(0D)
                .build());
        try {
            return parser.parse(response.getContent(), targetType);
        } catch (RuntimeException ex) {
            throw new ServiceException("结构化抽取 JSON 解析失败，targetType=" + targetType.getSimpleName()
                    + "，raw=" + abbreviate(response.getContent()), ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String abbreviate(String content) {
        if (content == null || content.length() <= RAW_OUTPUT_LIMIT) {
            return content;
        }
        return content.substring(0, RAW_OUTPUT_LIMIT);
    }
}
