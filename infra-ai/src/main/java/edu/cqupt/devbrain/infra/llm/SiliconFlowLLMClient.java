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

    public SiliconFlowLLMClient(AIModelProperties properties) {
        super(properties.getChat());
    }

    @Override
    public String provider() {
        return "siliconflow";
    }
}
