package edu.cqupt.devbrain.framework.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交幂等注解
 * <p>
 * 用于标记需要防止用户重复提交的 Controller 方法。通过分布式锁机制确保同一请求不会被重复处理。
 * </p>
 * <p>
 * 使用方式：在 Controller 的方法上添加 {@code @IdempotentSubmit} 注解即可。
 * 切面会自动根据请求路径、用户 ID 和参数生成锁的 Key，也可通过 SpEL 表达式自定义 Key。
 * </p>
 * <p>
 * 锁的 Key 生成策略：
 * <ul>
 *   <li>如果指定了 {@link #key()} 属性，使用 SpEL 表达式解析生成 Key</li>
 *   <li>否则，使用 "请求路径 + 用户ID + 参数MD5" 组合生成 Key</li>
 * </ul>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentSubmit {

    /**
     * 自定义幂等 Key 的 SpEL 表达式
     * <p>
     * 如果指定了此属性，将使用 SpEL 表达式解析结果作为分布式锁的 Key。
     * 支持引用方法参数，如 {@code #orderId}、{@code #user.id} 等。
     * 如果未指定，将使用默认策略（请求路径 + 用户ID + 参数MD5）生成 Key。
     * </p>
     *
     * @return SpEL 表达式字符串，默认为空（使用默认策略）
     */
    String key() default "";

    /**
     * 幂等校验失败时的错误提示信息
     * <p>
     * 当检测到重复提交时，抛出的 {@link edu.cqupt.devbrain.framework.exception.ClientException} 中包含的错误消息。
     * </p>
     *
     * @return 错误提示信息
     */
    String message() default "您操作太快，请稍后再试";
}
