package edu.cqupt.devbrain.infra.ai.gateway.chat;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目级AI对话请求。
 * 封装对话消息、温度、最大token数等参数，业务模块通过此接口与AI交互，不直接依赖具体AI框架。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    private Double temperature;

    private Integer maxTokens;

    private Boolean thinking;

    private edu.cqupt.devbrain.framework.convention.ChatRequest.ResponseFormat responseFormat;

    @Builder.Default
    private List<edu.cqupt.devbrain.framework.convention.ChatRequest.ToolDefinition> tools = new ArrayList<>();

    private String toolChoice;

    private Boolean parallelToolCalls;

    @Builder.Default
    private Map<String, Object> options = new HashMap<>();
}
