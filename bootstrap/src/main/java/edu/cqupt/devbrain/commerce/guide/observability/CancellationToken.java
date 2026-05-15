package edu.cqupt.devbrain.commerce.guide.observability;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Agent 运行取消标记。
 * <p>
 * 用于在工具执行过程中检查是否需要中断长时间运行的操作。
 * 通过 {@link GuideAgentToolContext} 传递给每个工具，工具在关键检查点调用 {@link #cancelled()} 判断是否应该终止。
 * <p>
 * 使用方式：
 * <pre>
 * // 在工具执行中定期检查
 * if (context.cancellationToken().cancelled()) {
 *     return GuideAgentToolResult.failed("search_products", "操作被取消", state, "CANCELLED", "用户取消");
 * }
 * </pre>
 *
 * @param cancelledSupplier 取消状态提供者（由上层传入，通常绑定到会话的 stop 标志）
 * @author liuqiyang
 * @since 2026-05-15
 */
public record CancellationToken(BooleanSupplier cancelledSupplier) {

    /** compact constructor — 防御性处理 null，未提供时默认不取消 */
    public CancellationToken {
        cancelledSupplier = Objects.requireNonNullElse(cancelledSupplier, () -> false);
    }

    /**
     * 检查是否已被取消。
     *
     * @return true 表示应该终止当前操作
     */
    public boolean cancelled() {
        return cancelledSupplier.getAsBoolean();
    }

    /**
     * 创建永不取消的令牌（用于不需要取消支持的场景）。
     *
     * @return 永不取消的 CancellationToken
     */
    public static CancellationToken none() {
        return new CancellationToken(() -> false);
    }
}
