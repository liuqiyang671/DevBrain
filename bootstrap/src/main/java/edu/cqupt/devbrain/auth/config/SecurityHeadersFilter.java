package edu.cqupt.devbrain.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 安全响应头过滤器 —— 为所有 HTTP 响应添加安全相关的响应头。
 * <p>
 * 遵循 OWASP 安全最佳实践，设置以下响应头：
 * <ul>
 *   <li><b>X-Content-Type-Options: nosniff</b> — 防止浏览器 MIME 类型嗅探，避免将非可执行资源当作可执行资源执行</li>
 *   <li><b>X-Frame-Options: DENY</b> — 禁止页面被嵌入 iframe，防止点击劫持攻击</li>
 *   <li><b>Referrer-Policy: strict-origin-when-cross-origin</b> — 控制 Referer 头的发送策略，保护用户隐私</li>
 *   <li><b>Content-Security-Policy: default-src 'self'; frame-ancestors 'none'</b> — 内容安全策略，限制资源加载来源</li>
 * </ul>
 * <p>
 * 继承 {@link OncePerRequestFilter}，确保每个请求仅过滤一次。
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /**
     * 为每个 HTTP 响应注入安全头，然后继续执行过滤器链。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
        filterChain.doFilter(request, response);
    }
}
