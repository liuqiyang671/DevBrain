package edu.cqupt.devbrain.infra.ai.gateway.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 项目级AI对话响应。
 * 标准化的AI返回结果，包含回答内容、思考过程和元数据，与具体AI供应商解耦。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private String content;

    private String thinkingContent;

    private String model;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
