package edu.cqupt.devbrain.infra.ai.llm;

/**
 * Handle returned by a streaming LLM invocation.
 */
@FunctionalInterface
public interface StreamCancellationHandle {

    void cancel();
}
