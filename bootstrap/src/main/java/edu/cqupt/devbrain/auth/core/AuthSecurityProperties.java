package edu.cqupt.devbrain.auth.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 认证安全配置属性 —— 集中管理认证模块的所有可配置参数。
 * <p>
 * 配置前缀：{@code devbrain.auth}，所有属性均可在 application.yml 中覆盖。
 * <p>
 * 配置项说明：
 * <ul>
 *   <li>jwtSecret — JWT 签名密钥，<b>生产环境务必替换默认值</b></li>
 *   <li>tokenTtl — JWT 令牌有效期，默认 8 小时</li>
 *   <li>csrfTtl — CSRF 令牌有效期，默认 2 小时</li>
 *   <li>tokenCookieName — JWT 令牌 Cookie 名称</li>
 *   <li>csrfCookieName — CSRF 令牌 Cookie 名称</li>
 *   <li>cookieSecure — 是否启用 Cookie Secure 标志（生产环境建议开启）</li>
 *   <li>sameSite — Cookie SameSite 策略，默认 Lax</li>
 *   <li>ipLoginMaxAttempts — 单 IP 时间窗口内最大登录尝试次数</li>
 *   <li>ipLoginWindow — IP 限流时间窗口</li>
 *   <li>accountMaxFailures — 账号连续登录失败锁定阈值</li>
 *   <li>accountLockDuration — 账号锁定时长</li>
 *   <li>publicPaths — 免认证公开路径列表，支持 Ant 风格匹配</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "devbrain.auth")
public class AuthSecurityProperties {

    private String jwtSecret = "change-me-devbrain-local-secret-please";
    private Duration tokenTtl = Duration.ofHours(8);
    private Duration csrfTtl = Duration.ofHours(2);
    private String tokenCookieName = "DEV_BRAIN_TOKEN";
    private String csrfCookieName = "XSRF-TOKEN";
    private boolean cookieSecure = false;
    private String sameSite = "Lax";
    private int ipLoginMaxAttempts = 20;
    private Duration ipLoginWindow = Duration.ofMinutes(5);
    private int accountMaxFailures = 5;
    private Duration accountLockDuration = Duration.ofMinutes(15);
    private List<String> publicPaths = List.of(
            "/auth/csrf",
            "/auth/register",
            "/auth/login",
            "/auth/password/forgot",
            "/auth/password/reset",
            "/error"
    );
}
