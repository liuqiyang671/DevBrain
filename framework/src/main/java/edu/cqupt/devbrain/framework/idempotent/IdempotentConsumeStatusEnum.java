package edu.cqupt.devbrain.framework.idempotent;

import java.util.Objects;

/**
 * 消息队列消费状态枚举
 * <p>
 * 定义消息消费过程中的状态标识，用于 {@link IdempotentConsume} 和 {@link IdempotentConsumeAspect} 的幂等控制。
 * </p>
 * <p>
 * 状态流转：CONSUMING（消费中） -> CONSUMED（已消费）
 * <ul>
 *   <li>{@link #CONSUMING} - 消息正在消费中，此时如果收到重复消息将触发延迟重试</li>
 *   <li>{@link #CONSUMED} - 消息已成功消费完成，重复消息将被直接跳过</li>
 * </ul>
 * </p>
 */
public enum IdempotentConsumeStatusEnum {

    /**
     * 消费中 - 消息正在被消费处理
     * <p>
     * 当收到重复消息时，如果状态为 CONSUMING，将触发延迟重试。
     * </p>
     */
    CONSUMING("0"),

    /**
     * 已消费 - 消息已成功消费完成
     * <p>
     * 消息处理成功后更新为此状态，后续重复消息将被直接跳过。
     * </p>
     */
    CONSUMED("1");

    /** 状态码 */
    private final String code;

    /**
     * 构造消费状态枚举
     *
     * @param code 状态码
     */
    IdempotentConsumeStatusEnum(String code) {
        this.code = code;
    }

    /**
     * 获取状态码
     *
     * @return 状态码字符串
     */
    public String getCode() {
        return code;
    }

    /**
     * 判断当前消费状态是否为异常状态（消费中）
     * <p>
     * 当消息状态为 CONSUMING（消费中）时，表示另一条相同消息正在被消费，
     * 此时返回 true，触发延迟重试机制。
     * </p>
     *
     * @param consumeStatus 当前消费状态码
     * @return 如果状态为 CONSUMING（消费中）返回 true，否则返回 false
     */
    public static boolean isError(String consumeStatus) {
        return Objects.equals(CONSUMING.code, consumeStatus);
    }
}
