package edu.cqupt.devbrain.rag.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chat 流式接口并发控制注解，基于 Redis 信号量限制同时处理的请求数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChatQueueLimiter {

    /** 最大并发数。 */
    int maxConcurrent() default 10;

    /** 获取信号量的最大等待时间（毫秒），0 表示不等待。 */
    long waitMillis() default 0;

    /** 信号量 key，默认使用方法名。 */
    String key() default "stream";
}
