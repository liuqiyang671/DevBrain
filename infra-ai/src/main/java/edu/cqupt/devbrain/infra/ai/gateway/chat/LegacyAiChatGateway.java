package edu.cqupt.devbrain.infra.ai.gateway.chat;

import edu.cqupt.devbrain.framework.convention.ChatRequest;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.ai.llm.StreamCallback;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;

/**
 * 基于原有 LLMService 的对话网关适配器。
 * 将项目已有的 LLMService 路由能力适配到新的 AiChatGateway 接口。
 */
public class LegacyAiChatGateway implements AiChatGateway {

    private final LLMService llmService;

    public LegacyAiChatGateway(LLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        AccumulatingStreamCallback callback = new AccumulatingStreamCallback();
        llmService.streamChat(toChatRequest(request), callback);
        return AiChatResponse.builder()
                .content(callback.content())
                .metadata(request == null ? null : request.getOptions())
                .build();
    }

    @Override
    public StreamCancellationHandle stream(AiChatRequest request, AiStreamHandler handler) {
        return llmService.streamChat(toChatRequest(request), new StreamCallback() {
            @Override
            public void onContent(String content) {
                if (handler != null) {
                    handler.onContent(content);
                }
            }

            @Override
            public void onThinking(String thinking) {
                if (handler != null) {
                    handler.onThinking(thinking);
                }
            }

            @Override
            public void onTrace(String stage, String message) {
                if (handler != null) {
                    handler.onTrace(stage, message);
                }
            }

            @Override
            public void onComplete() {
                if (handler != null) {
                    handler.onComplete();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (handler != null) {
                    handler.onError(throwable);
                }
            }
        });
    }

    private ChatRequest toChatRequest(AiChatRequest request) {
        if (request == null) {
            return ChatRequest.builder().build();
        }
        return ChatRequest.builder()
                .messages(request.getMessages())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .thinking(request.getThinking())
                .responseFormat(request.getResponseFormat())
                .tools(request.getTools())
                .toolChoice(request.getToolChoice())
                .parallelToolCalls(request.getParallelToolCalls())
                .build();
    }

    private static final class AccumulatingStreamCallback implements StreamCallback {

        private final StringBuilder content = new StringBuilder();

        @Override
        public void onContent(String content) {
            this.content.append(content);
        }

        @Override
        public void onThinking(String thinking) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable throwable) {
            throw throwable instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException(throwable);
        }

        private String content() {
            return content.toString();
        }
    }
}
