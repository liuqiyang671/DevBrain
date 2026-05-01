package edu.cqupt.devbrain.framework.trace;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * RAG trace 注解切面。
 */
@Aspect
@Component
public class RagTraceAspect {

    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

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
