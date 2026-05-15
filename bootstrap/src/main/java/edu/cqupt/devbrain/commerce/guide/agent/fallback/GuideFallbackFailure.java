package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import java.util.Map;

/**
 * 进入兜底策略时的失败摘要。
 * <p>
 * 记录导致进入兜底策略的失败信息，用于：
 * <ul>
 *   <li><b>兜底决策</b>：根据失败类型选择合适的恢复动作</li>
 *   <li><b>可观测性</b>：记录失败原因用于监控和告警</li>
 *   <li><b>调试</b>：追踪失败链路</li>
 * </ul>
 *
 * @param type     失败类型（{@link FallbackFailureType}）
 * @param summary  失败摘要（人类可读的描述）
 * @param errorCode 错误码（用于监控和告警）
 * @param metadata 附加元数据（如失败的工具名、参数等）
 * @author liuqiyang
 * @since 2026-05-15
 * @see FallbackFailureType 失败类型枚举
 * @see GuideFallbackPolicy 兜底策略
 */
public record GuideFallbackFailure(
        FallbackFailureType type,
        String summary,
        String errorCode,
        Map<String, Object> metadata
) {

    /** compact constructor — 防御性处理 null 字段 */
    public GuideFallbackFailure {
        type = type == null ? FallbackFailureType.TOOL_RUNTIME_FAILED : type;
        summary = summary == null ? "" : summary;
        errorCode = errorCode == null ? "" : errorCode;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 创建简单的失败摘要。
     *
     * @param type    失败类型
     * @param summary 失败摘要
     * @return 失败摘要实例
     */
    public static GuideFallbackFailure of(FallbackFailureType type, String summary) {
        return new GuideFallbackFailure(type, summary, "", Map.of());
    }

    /**
     * 创建完整的失败摘要。
     *
     * @param type      失败类型
     * @param summary   失败摘要
     * @param errorCode 错误码
     * @param metadata  附加元数据
     * @return 失败摘要实例
     */
    public static GuideFallbackFailure of(FallbackFailureType type,
                                          String summary,
                                          String errorCode,
                                          Map<String, Object> metadata) {
        return new GuideFallbackFailure(type, summary, errorCode, metadata);
    }
}
