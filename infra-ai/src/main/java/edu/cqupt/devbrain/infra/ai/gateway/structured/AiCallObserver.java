package edu.cqupt.devbrain.infra.ai.gateway.structured;

/**
 * AI 调用观察器扩展点。
 * <p>
 * infra-ai 只产生调用事实，具体落库或上报监控由业务模块提供实现。
 */
public interface AiCallObserver {

    default void onStart(AiCallRecord record) {
    }

    default void onComplete(AiCallRecord record) {
    }
}
