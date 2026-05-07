package edu.cqupt.devbrain.infra.ai.llm;

import edu.cqupt.devbrain.framework.convention.ChatMessage;
import edu.cqupt.devbrain.framework.convention.ChatRequest;

import java.util.List;

/**
 * LLM service entry point for synchronous and streaming chat calls.
 */
public interface LLMService {

    /**
     * Send a synchronous prompt to the model.
     */
    String chat(String prompt);

    /**
     * Stream a chat request. Providers can override this with native streaming; the default bridges to {@link #chat(String)}.
     */
    default StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        try {
            String answer = chat(toPrompt(request));
            if (callback != null) {
                callback.onContent(answer);
                callback.onComplete();
            }
        } catch (Throwable ex) {
            if (callback != null) {
                callback.onError(ex);
            } else {
                throw ex;
            }
        }
        return () -> {
        };
    }

    private String toPrompt(ChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        List<ChatMessage> messages = request.getMessages();
        StringBuilder prompt = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            if (!prompt.isEmpty()) {
                prompt.append("\n\n");
            }
            String role = message.getRole() == null ? "user" : message.getRole().name().toLowerCase();
            prompt.append(role).append(": ").append(message.getContent() == null ? "" : message.getContent());
        }
        return prompt.toString();
    }
}
