package edu.cqupt.devbrain.auth.core;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * Cookie 辅助工具 —— 统一管理认证相关 Cookie 的读写操作。
 * <p>
 * 封装了 JWT 令牌 Cookie 和 CSRF 令牌 Cookie 的写入、清除与读取逻辑，
 * 确保所有 Cookie 属性（Path、SameSite、HttpOnly、Secure、Max-Age）的一致性。
 * <p>
 * <b>安全策略</b>：
 * <ul>
 *   <li>JWT 令牌 Cookie 设置 HttpOnly，防止 JavaScript 读取（防 XSS）</li>
 *   <li>CSRF 令牌 Cookie 不设置 HttpOnly，允许前端 JavaScript 读取后放入请求头</li>
 *   <li>SameSite 默认 Lax，兼顾安全与可用性</li>
 *   <li>生产环境应开启 Secure 标志，仅通过 HTTPS 传输</li>
 * </ul>
 */
@Component
public class CookieSupport {

    private final AuthSecurityProperties properties;

    public CookieSupport(AuthSecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * 写入 JWT 令牌 Cookie（HttpOnly）。
     *
     * @param response HTTP 响应对象
     * @param token    JWT 令牌值
     */
    public void writeTokenCookie(HttpServletResponse response, String token) {
        addCookie(response, properties.getTokenCookieName(), token, true, properties.getTokenTtl());
    }

    /**
     * 清除 JWT 令牌 Cookie（设置 Max-Age=0 使浏览器立即删除）。
     *
     * @param response HTTP 响应对象
     */
    public void clearTokenCookie(HttpServletResponse response) {
        addCookie(response, properties.getTokenCookieName(), "", true, Duration.ZERO);
    }

    /**
     * 清除 CSRF 令牌 Cookie。
     *
     * @param response HTTP 响应对象
     */
    public void clearCsrfCookie(HttpServletResponse response) {
        addCookie(response, properties.getCsrfCookieName(), "", false, Duration.ZERO);
    }

    /**
     * 写入 CSRF 令牌 Cookie（非 HttpOnly，前端可读取）。
     *
     * @param response HTTP 响应对象
     * @param token    CSRF 令牌值
     */
    public void writeCsrfCookie(HttpServletResponse response, String token) {
        addCookie(response, properties.getCsrfCookieName(), token, false, properties.getCsrfTtl());
    }

    /**
     * 从请求中读取 JWT 令牌 Cookie 的值。
     *
     * @param request HTTP 请求对象
     * @return 令牌值，若不存在则返回 null
     */
    public String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.getTokenCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 底层 Cookie 写入方法，通过 Set-Cookie 响应头设置 Cookie。
     * <p>
     * 统一设置 Path=/、SameSite、HttpOnly、Secure 等安全属性。
     * Max-Age 最小值为 0（用于清除 Cookie），不会出现负值。
     *
     * @param response HTTP 响应对象
     * @param name     Cookie 名称
     * @param value    Cookie 值
     * @param httpOnly 是否设置 HttpOnly 标志
     * @param maxAge   Cookie 有效时长，Duration.ZERO 表示立即过期（清除）
     */
    private void addCookie(HttpServletResponse response, String name, String value, boolean httpOnly, Duration maxAge) {
        StringBuilder header = new StringBuilder();
        header.append(name).append("=").append(value == null ? "" : value)
                .append("; Path=/")
                .append("; Max-Age=").append(Math.max(0, maxAge.toSeconds()))
                .append("; SameSite=").append(properties.getSameSite());
        if (httpOnly) {
            header.append("; HttpOnly");
        }
        if (properties.isCookieSecure()) {
            header.append("; Secure");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, header.toString());
    }
}
