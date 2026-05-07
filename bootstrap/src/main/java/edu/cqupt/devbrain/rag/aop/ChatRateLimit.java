package edu.cqupt.devbrain.rag.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chat 接口限流注解，基于 Redis 固定窗口计数器实现。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChatRateLimit {

    /** 窗口内最大请求数。 */
    int limit() default 5;

    /** 限流窗口时长（秒）。 */
    int windowSeconds() default 60;
}
