package edu.cqupt.devbrain.rag.aop;

import cn.hutool.crypto.digest.DigestUtil;
import edu.cqupt.devbrain.framework.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

final class ChatAopSupport {

    private ChatAopSupport() {
    }

    static String userId() {
        String userId = UserContext.getUserId();
        if (StringUtils.hasText(userId)) {
            return userId.trim();
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "anonymous";
        }
        String headerUserId = request.getHeader("X-User-Id");
        if (StringUtils.hasText(headerUserId)) {
            return headerUserId.trim();
        }
        return "anonymous";
    }

    static Method targetMethod(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return joinPoint.getTarget().getClass()
                .getDeclaredMethod(signature.getName(), signature.getParameterTypes());
    }

    static String methodKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getName();
    }

    static String requestParamOrArg(ProceedingJoinPoint joinPoint, String name, int fallbackArgIndex) {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String value = request.getParameter(name);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        Object[] args = joinPoint.getArgs();
        if (args != null && fallbackArgIndex >= 0 && fallbackArgIndex < args.length && args[fallbackArgIndex] != null) {
            return String.valueOf(args[fallbackArgIndex]).trim();
        }
        return "";
    }

    static String md5(String value) {
        return DigestUtil.md5Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }
}
