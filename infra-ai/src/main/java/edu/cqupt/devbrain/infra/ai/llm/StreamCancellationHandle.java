package edu.cqupt.devbrain.infra.ai.llm;

/**
 * 流式 LLM 调用返回的取消句柄，允许调用方在流式输出进行中主动终止。
 */
@FunctionalInterface
public interface StreamCancellationHandle {

    /**
     * 取消正在进行的流式调用。调用后底层连接将被中断，不再触发后续回调。
     */
    void cancel();
}
