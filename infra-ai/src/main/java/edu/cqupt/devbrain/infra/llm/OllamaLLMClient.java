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

    /**
     * 注入 AI 模型配置，使用聊天模型的超时参数构造 HTTP 客户端。
     *
     * @param properties AI 模型配置属性
     */
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

    /**
     * Ollama 使用 reasoning_effort 替代 OpenAI 的 enable_thinking 参数来控制思维链深度。
     */
    @Override
    protected void customizeRequestBody(JsonObject body, ChatTarget target) {
        if (body.has("enable_thinking")) {
            body.remove("enable_thinking");
            body.addProperty("reasoning_effort", "medium");
        }
    }
}
