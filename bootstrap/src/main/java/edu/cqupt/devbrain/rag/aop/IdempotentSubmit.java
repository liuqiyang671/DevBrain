package edu.cqupt.devbrain.rag.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chat 提交幂等保护注解，防止短时间内重复提交相同问题。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentSubmit {

    /** 幂等 key 过期时间（秒）。 */
    int expireSeconds() default 10;

    /** 重复提交时的提示信息。 */
    String message() default "相同问题正在处理中，请稍后再试";
}
