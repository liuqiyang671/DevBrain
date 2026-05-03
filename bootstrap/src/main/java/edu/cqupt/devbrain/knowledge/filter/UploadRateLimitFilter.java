package edu.cqupt.devbrain.knowledge.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.knowledge.config.UploadRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 上传限流过滤器 —— 基于 Redisson 信号量限制上传接口并发数。
 * <p>
 * 在 multipart 请求体解析之前拦截，避免大文件请求占用服务器资源后才被限流。
 * 仅检查 HTTP method、requestURI、contentType，不触发 multipart 解析。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
public class UploadRateLimitFilter extends OncePerRequestFilter {

    private static final String METHOD_POST = "POST";
    private static final String CONTENT_TYPE_MULTIPART = "multipart/form-data";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final UploadRateLimitProperties properties;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 非启用状态直接放行
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 仅拦截 POST + multipart/form-data 请求
        if (!METHOD_POST.equals(request.getMethod())
                || !isMultipartContentType(request.getContentType())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 路径匹配（相对于 context-path）
        String path = getRequestPath(request);
        if (!matchesAny(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取信号量并尝试获取许可
        RSemaphore semaphore = redissonClient.getSemaphore(properties.getSemaphoreName());
        boolean acquired;
        try {
            if (properties.getWaitMillis() > 0) {
                acquired = semaphore.tryAcquire(properties.getWaitMillis(), TimeUnit.MILLISECONDS);
            } else {
                acquired = semaphore.tryAcquire();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("上传限流信号量等待被中断，path={}", path);
            filterChain.doFilter(request, response);
            return;
        }

        if (!acquired) {
            log.warn("上传限流触发，拒绝请求，path={}, permits={}", path, properties.getPermits());
            writeTooManyRequests(response);
            return;
        }

        // 持有许可，执行后续链路，finally 中释放
        try {
            filterChain.doFilter(request, response);
        } finally {
            semaphore.release();
        }
    }

    /**
     * 判断 Content-Type 是否为 multipart/form-data。
     * 仅使用 request.getContentType()，不触发 multipart 解析。
     */
    private boolean isMultipartContentType(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith(CONTENT_TYPE_MULTIPART);
    }

    /**
     * 获取相对于 context-path 的请求路径。
     * 仅使用 getRequestURI() 和 getContextPath()，不触发 multipart 解析。
     */
    private String getRequestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * 使用 AntPathMatcher 匹配路径。
     */
    private boolean matchesAny(String path) {
        for (String pattern : properties.getPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入 429 响应，使用项目统一响应结构。
     */
    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        Result<Void> result = Results.failure(String.valueOf(HTTP_TOO_MANY_REQUESTS),
                "当前上传人数较多，请稍后再试");
        response.setStatus(HTTP_TOO_MANY_REQUESTS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), result);
    }
}
