package edu.cqupt.devbrain.framework.trace;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * RAG 链路追踪切面，拦截 {@link RagTraceRoot} 和 {@link RagTraceNode} 注解的方法。
 * <p>
 * 职责：
 * <ul>
 *     <li>拦截 {@code @RagTraceRoot} 方法：初始化 trace 上下文（traceId、taskId），方法结束后清理</li>
 *     <li>拦截 {@code @RagTraceNode} 方法：将节点名称压入调用栈，方法结束后弹出</li>
 *     <li>通过反射解析方法参数，自动提取 conversationId 和 taskId 等上下文信息</li>
 * </ul>
 */
@Aspect
@Component
public class RagTraceAspect {

    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /**
     * 拦截 @RagTraceRoot 注解方法，管理 trace 生命周期。
     * 仅在当前线程无活跃 trace 时创建新 trace，支持嵌套调用复用。
     */
    @Around("@annotation(ragTraceRoot)")
    public Object aroundRoot(ProceedingJoinPoint joinPoint, RagTraceRoot ragTraceRoot) throws Throwable {
        boolean owner = !RagTraceContext.hasTrace();
        if (owner) {
            RagTraceContext.begin(resolveArgument(joinPoint, ragTraceRoot.conversationIdArg()));
            RagTraceContext.setTaskId(resolveArgument(joinPoint, ragTraceRoot.taskIdArg()));
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (owner) {
                RagTraceContext.end();
            }
        }
    }

    /**
     * 拦截 @RagTraceNode 注解方法，将节点名称压入/弹出调用栈。
     * 若注解未指定 name，则默认使用方法名作为节点标识。
     */
    @Around("@annotation(ragTraceNode)")
    public Object aroundNode(ProceedingJoinPoint joinPoint, RagTraceNode ragTraceNode) throws Throwable {
        String nodeId = ragTraceNode.name().isBlank()
                ? ((MethodSignature) joinPoint.getSignature()).getMethod().getName()
                : ragTraceNode.name();
        RagTraceContext.pushNode(nodeId);
        try {
            return joinPoint.proceed();
        } finally {
            RagTraceContext.popNode();
        }
    }

    /**
     * 从方法参数中按名称解析参数值，用于提取 conversationId、taskId 等上下文参数。
     */
    private String resolveArgument(ProceedingJoinPoint joinPoint, String parameterName) {
        if (parameterName == null || parameterName.isBlank()) {
            return null;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        if (parameterNames == null) {
            return null;
        }
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterName.equals(parameterNames[i]) && i < args.length && args[i] != null) {
                return String.valueOf(args[i]);
            }
        }
        return null;
    }
}
