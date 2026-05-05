package edu.cqupt.devbrain.infra.ai.llm;

import edu.cqupt.devbrain.framework.exception.RemoteException;

/**
 * 默认 LLM 占位实现，避免未配置聊天模型时影响应用启动。
 */
public class UnavailableLLMService implements LLMService {

    /**
     * 没有真实 LLM Bean 时，只有实际执行增强任务才抛出清晰异常。
     */
    @Override
    public String chat(String prompt) {
        throw new RemoteException("LLM 服务未配置，请提供 LLMService 实现后再执行 AI 增强节点");
    }
}
