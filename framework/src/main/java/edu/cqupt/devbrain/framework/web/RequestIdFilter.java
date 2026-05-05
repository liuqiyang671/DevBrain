package edu.cqupt.devbrain.framework.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求 ID 过滤器。
 * <p>为每个 HTTP 请求生成或提取唯一标识 {@code X-Request-Id}，并执行以下操作：</p>
 * <ul>
 *   <li>将 requestId 写入 SLF4J MDC，使日志自动携带请求 ID，便于链路追踪</li>
 *   <li>将 requestId 写回响应头，方便前端或网关层关联日志</li>
 * </ul>
 * <p>若客户端请求头中已携带 {@code X-Request-Id}，则复用该值；否则自动生成 UUID。</p>
 *
 * @see RequestIdContext
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = normalize(request.getHeader(RequestIdContext.HEADER_NAME));
        response.setHeader(RequestIdContext.HEADER_NAME, requestId);
        try (RequestIdContext.Scope ignored = RequestIdContext.open(requestId)) {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 规范化请求 ID：若为空则生成新的 UUID（去除短横线），否则去除首尾空白。
     */
    private String normalize(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return requestId.trim();
    }
}
