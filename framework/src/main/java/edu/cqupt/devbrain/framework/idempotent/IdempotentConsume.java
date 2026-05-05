package edu.cqupt.devbrain.framework.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复消费幂等注解
 * <p>
 * 用于标记消息队列消费者方法，防止同一条消息被重复消费。
 * 通过 Redis 的 SET NX（不存在才设置）机制实现幂等控制。
 * </p>
 * <p>
 * 使用方式：在 MQ 消费者方法上添加 {@code @IdempotentConsume} 注解，并通过 {@link #key()} 指定唯一标识。
 * </p>
 * <p>
 * 消费状态流转：
 * <ol>
 *   <li>消息首次消费 - 状态设置为 CONSUMING（消费中）</li>
 *   <li>消息消费成功 - 状态更新为 CONSUMED（已消费）</li>
 *   <li>消息消费失败 - 删除幂等标记，允许后续重新消费</li>
 * </ol>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentConsume {

    /**
     * 防重令牌 Key 的前缀
     * <p>
     * 用于区分不同业务场景的消费幂等 Key，避免 Key 冲突。
     * 最终 Key 格式：{keyPrefix}{spel解析结果}
     * </p>
     *
     * @return Key 前缀字符串，默认为空
     */
    String keyPrefix() default "";

    /**
     * 唯一标识消息的 SpEL 表达式（必填）
     * <p>
     * 通过 SpEL 表达式从方法参数中提取消息的唯一标识（如消息 ID、订单号等）。
     * 支持引用方法参数，如 {@code #messageId}、{@code #order.id} 等。
     * </p>
     *
     * @return SpEL 表达式字符串
     */
    String key();

    /**
     * 防重令牌 Key 的过期时间（单位：秒）
     * <p>
     * Redis 中幂等 Key 的 TTL（生存时间），超过此时间后 Key 将自动删除。
     * 默认为 3600 秒（1 小时），可根据业务场景调整。
     * </p>
     *
     * @return 过期时间（秒），默认 3600
     */
    long keyTimeout() default 3600L;
}
