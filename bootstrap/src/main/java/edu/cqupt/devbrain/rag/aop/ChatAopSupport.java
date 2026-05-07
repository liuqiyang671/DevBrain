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

/**
 * Chat AOP 切面工具类，提供用户身份识别、请求指纹生成等通用方法。
 */
final class ChatAopSupport {

    private ChatAopSupport() {
    }

    /**
     * 获取当前用户 ID，优先从 UserContext 读取，其次从请求头 X-User-Id 读取。
     */
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

    /**
     * 通过反射获取切点目标方法。
     */
    static Method targetMethod(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return joinPoint.getTarget().getClass()
                .getDeclaredMethod(signature.getName(), signature.getParameterTypes());
    }

    /**
     * 获取切点方法名，用作限流 key 的一部分。
     */
    static String methodKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getName();
    }

    /**
     * 从 HTTP 请求参数或方法参数中提取值，用于构建请求指纹。
     *
     * @param name            请求参数名
     * @param fallbackArgIndex 参数列表中的兜底索引
     */
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

    /**
     * 计算字符串的 MD5 哈希值，用于生成请求指纹。
     */
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
