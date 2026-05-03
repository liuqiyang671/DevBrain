package edu.cqupt.devbrain.auth.core;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * CSRF 令牌服务 —— 防止跨站请求伪造攻击。
 * <p>
 * 采用"双重提交 Cookie"模式：
 * <ol>
 *   <li>前端请求 CSRF 令牌时，服务端生成随机令牌，同时写入 Cookie 和缓存</li>
 *   <li>前端在后续写操作请求中，通过 X-XSRF-TOKEN 请求头携带令牌</li>
 *   <li>服务端校验请求头中的令牌与 Cookie 中的令牌是否一致，且令牌在缓存中存在</li>
 * </ol>
 * <p>
 * <b>校验规则</b>：仅对非安全 HTTP 方法（POST、PUT、PATCH、DELETE）执行 CSRF 校验，
 * GET、HEAD、OPTIONS 等安全方法跳过校验。
 */
@Service
@RequiredArgsConstructor
public class CsrfTokenService {

    private final SecurityCache cache;
    private final AuthSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 签发 CSRF 令牌。
     * <p>
     * 使用 {@link SecureRandom} 生成 24 字节随机数，转为 48 字符十六进制字符串，
     * 同时在缓存中记录该令牌（TTL 由 csrfTtl 配置），用于后续校验。
     *
     * @return 生成的 CSRF 令牌字符串
     */
    public String issueToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        cache.set(csrfKey(token), "1", properties.getCsrfTtl());
        return token;
    }

    /**
     * 校验请求中的 CSRF 令牌是否合法。
     * <p>
     * 校验条件（全部满足才通过）：
     * <ul>
     *   <li>请求头 X-XSRF-TOKEN 非空</li>
     *   <li>Cookie 中 XSRF-TOKEN 非空</li>
     *   <li>请求头与 Cookie 中的令牌值一致</li>
     *   <li>令牌在服务端缓存中存在（未过期且未被伪造）</li>
     * </ul>
     *
     * @param request HTTP 请求对象
     * @throws InvalidTokenException CSRF 校验失败
     */
    public void validate(HttpServletRequest request) {
        String header = request.getHeader("X-XSRF-TOKEN");
        String cookie = readCookie(request, properties.getCsrfCookieName());
        if (header == null || cookie == null || !header.equals(cookie) || cache.get(csrfKey(header)).isEmpty()) {
            throw new InvalidTokenException("CSRF 校验失败");
        }
    }

    /**
     * 从请求的 Cookie 中读取指定名称的值。
     */
    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 生成 CSRF 令牌的缓存键，格式：auth:csrf:{token}
     */
    private String csrfKey(String token) {
        return "auth:csrf:" + token;
    }
}
