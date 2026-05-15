package edu.cqupt.devbrain.infra.ai.gateway.chat;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 基于 Spring AI 的对话网关适配器。
 * 仅在 provider=spring-ai 且应用上下文中存在 ChatClient.Builder 时注册。
 */
public class SpringAiChatGateway implements AiChatGateway {

    private final ChatClient chatClient;

    public SpringAiChatGateway(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        String content = chatClient.prompt(toPrompt(request)).call().content();
        return AiChatResponse.builder()
                .content(content)
                .metadata(request == null ? null : request.getOptions())
                .build();
    }

    @Override
    public StreamCancellationHandle stream(AiChatRequest request, AiStreamHandler handler) {
        try {
            String content = chat(request).getContent();
            if (handler != null) {
                handler.onContent(content);
                handler.onComplete();
            }
        } catch (Throwable throwable) {
            if (handler != null) {
                handler.onError(throwable);
            } else if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                throw new IllegalStateException(throwable);
            }
        }
        return () -> {
        };
    }

    private String toPrompt(AiChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        for (ChatMessage message : request.getMessages()) {
            if (!prompt.isEmpty()) {
                prompt.append("\n\n");
            }
            prompt.append(message.getRole() == null ? "user" : message.getRole().name().toLowerCase())
                    .append(": ")
                    .append(message.getContent() == null ? "" : message.getContent());
        }
        return prompt.toString();
    }
}
