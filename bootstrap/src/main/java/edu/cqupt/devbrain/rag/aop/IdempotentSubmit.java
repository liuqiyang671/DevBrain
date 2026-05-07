package edu.cqupt.devbrain.rag.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Short-window idempotent protection for chat submissions.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentSubmit {

    int expireSeconds() default 10;

    String message() default "相同问题正在处理中，请稍后再试";
}
