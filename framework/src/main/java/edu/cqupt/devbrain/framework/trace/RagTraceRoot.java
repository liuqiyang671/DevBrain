package edu.cqupt.devbrain.framework.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RAG 链路追踪根节点注解，标记在方法上表示一次完整的 RAG 请求入口。
 * <p>
 * 由 {@link RagTraceAspect} 拦截，在方法执行前初始化 traceId，
 * 方法执行后清理上下文。支持通过参数名自动提取 conversationId 和 taskId。
 * <p>
 * 若当前线程已存在活跃的 trace，则复用已有 traceId，避免重复创建。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RagTraceRoot {

    /**
     * 链路名称（用于展示）
     */
    String name() default "";

    /**
     * 会话 ID 参数名
     */
    String conversationIdArg() default "conversationId";

    /**
     * 任务 ID 参数名
     */
    String taskIdArg() default "taskId";
}
