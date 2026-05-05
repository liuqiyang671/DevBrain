package edu.cqupt.devbrain.framework.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RAG 链路追踪普通节点注解，标记在方法上表示 RAG 处理流程中的一个子步骤。
 * <p>
 * 由 {@link RagTraceAspect} 拦截，在方法执行前将节点压入调用栈，
 * 方法执行后弹出。节点嵌套关系自动通过栈结构维护。
 * <p>
 * 典型场景：文档检索、向量召回、重排序、LLM 生成等环节。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RagTraceNode {

    /**
     * 节点名称（用于展示）
     */
    String name() default "";

    /**
     * 节点类型（用于分组统计）
     */
    String type() default "METHOD";
}
