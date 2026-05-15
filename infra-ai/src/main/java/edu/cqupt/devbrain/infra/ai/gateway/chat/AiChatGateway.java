package edu.cqupt.devbrain.infra.ai.gateway.chat;

import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;

/**
 * 项目级AI对话网关接口。
 * 业务模块应依赖此接口而非直接使用 Spring AI、LangChain4j 或供应商SDK。
 * 提供同步对话和流式对话两种能力。
 */
public interface AiChatGateway {

    AiChatResponse chat(AiChatRequest request);

    StreamCancellationHandle stream(AiChatRequest request, AiStreamHandler handler);
}
