package edu.cqupt.devbrain.infra.llm;

import com.google.gson.JsonObject;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import org.springframework.stereotype.Component;

/**
 * Ollama 本地 LLM 客户端。
 * <p>
 * Ollama 提供 OpenAI 兼容 chat/completions 接口，本地调用通常不需要 API Key。
 */
@Component
public class OllamaLLMClient extends AbstractOpenAIStyleLLMClient {

    public OllamaLLMClient(AIModelProperties properties) {
        super(properties.getChat());
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    @Override
    protected void customizeRequestBody(JsonObject body, ChatTarget target) {
        if (body.has("enable_thinking")) {
            body.remove("enable_thinking");
            body.addProperty("reasoning_effort", "medium");
        }
    }
}
