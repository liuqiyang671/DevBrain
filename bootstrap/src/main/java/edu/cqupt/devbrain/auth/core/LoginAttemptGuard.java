package edu.cqupt.devbrain.auth.core;

import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 登录尝试守卫 —— 防止暴力破解和恶意登录攻击。
 * <p>
 * 提供两级防护策略：
 * <ul>
 *   <li><b>IP 级限流</b>：同一 IP 在指定时间窗口内登录次数超过上限时，触发 {@link RateLimitExceededException}。</li>
 *   <li><b>账号级锁定</b>：同一账号连续登录失败次数达到阈值后自动锁定，锁定期间禁止登录。</li>
 * </ul>
 * 所有计数与锁定状态均通过 {@link SecurityCache}（Redis）存储，支持 TTL 自动过期。
 */
@RequiredArgsConstructor
public class LoginAttemptGuard {

    private final SecurityCache cache;
    private final AuthSecurityProperties properties;

    /**
     * 检查当前登录请求是否被允许。
     * <p>
     * 执行顺序：先检查 IP 限流，再检查账号锁定。任一条件不满足即抛出异常，阻止登录。
     *
     * @param username  待登录的用户名
     * @param ipAddress 请求来源 IP 地址
     * @throws RateLimitExceededException IP 登录频率超限
     * @throws AccountLockedException     账号已被锁定
     */
    public void checkLoginAllowed(String username, String ipAddress) {
        long ipAttempts = cache.increment(ipKey(ipAddress), properties.getIpLoginWindow());
        if (ipAttempts > properties.getIpLoginMaxAttempts()) {
            throw new RateLimitExceededException();
        }
        if (cache.get(lockKey(username)).isPresent()) {
            throw new AccountLockedException();
        }
    }

    /**
     * 记录一次登录失败。
     * <p>
     * 失败计数递增后，若达到 {@link AuthSecurityProperties#getAccountMaxFailures()} 阈值，
     * 则在缓存中写入锁定标记，锁定时长与失败计数 TTL 一致。
     *
     * @param username 登录失败的用户名
     */
    public void recordFailure(String username) {
        long failures = cache.increment(failureKey(username), properties.getAccountLockDuration());
        if (failures >= properties.getAccountMaxFailures()) {
            cache.set(lockKey(username), "1", properties.getAccountLockDuration());
        }
    }

    /**
     * 记录一次登录成功，清除该账号的失败计数与锁定标记。
     *
     * @param username 登录成功的用户名
     */
    public void recordSuccess(String username) {
        cache.delete(failureKey(username));
        cache.delete(lockKey(username));
    }

    /**
     * 生成 IP 限流的缓存键，格式：auth:ip:{ipAddress}
     */
    private String ipKey(String ipAddress) {
        return "auth:ip:" + normalize(ipAddress);
    }

    /**
     * 生成账号失败计数的缓存键，格式：auth:account:failure:{username}
     */
    private String failureKey(String username) {
        return "auth:account:failure:" + normalize(username);
    }

    /**
     * 生成账号锁定标记的缓存键，格式：auth:account:lock:{username}
     */
    private String lockKey(String username) {
        return "auth:account:lock:" + normalize(username);
    }

    /**
     * 规范化字符串：去除首尾空白、转小写。空或空白值统一为 "unknown"，
     * 防止因大小写或空白差异导致计数绕过。
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "unknown";
    }
}
