package edu.cqupt.devbrain.infra.ai.llm;

/**
 * 大语言模型服务接口，为文档增强、块级元数据提取等能力提供统一聊天入口。
 */
public interface LLMService {

    /**
     * 发送同步聊天请求并返回模型文本结果。
     *
     * @param prompt 提示词
     * @return 模型返回文本
     */
    String chat(String prompt);
}
