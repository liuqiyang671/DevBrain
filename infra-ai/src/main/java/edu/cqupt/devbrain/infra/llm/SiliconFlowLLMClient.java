package edu.cqupt.devbrain.infra.llm;

import edu.cqupt.devbrain.infra.config.AIModelProperties;
import org.springframework.stereotype.Component;

/**
 * SiliconFlow LLM 客户端。
 * <p>
 * SiliconFlow 兼容 OpenAI chat/completions 协议，远程调用需要 API Key。
 */
@Component
public class SiliconFlowLLMClient extends AbstractOpenAIStyleLLMClient {

    /**
     * 注入 AI 模型配置，使用聊天模型的超时参数构造 HTTP 客户端。
     *
     * @param properties AI 模型配置属性
     */
    public SiliconFlowLLMClient(AIModelProperties properties) {
        super(properties.getChat());
    }

    @Override
    public String provider() {
        return "siliconflow";
    }
}
