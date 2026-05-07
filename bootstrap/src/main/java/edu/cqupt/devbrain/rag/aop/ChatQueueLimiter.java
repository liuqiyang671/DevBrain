package edu.cqupt.devbrain.rag.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Semaphore based concurrency limiter for streaming chat endpoints.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChatQueueLimiter {

    int maxConcurrent() default 10;

    long waitMillis() default 0;

    String key() default "stream";
}
