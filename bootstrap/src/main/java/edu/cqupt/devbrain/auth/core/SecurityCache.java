package edu.cqupt.devbrain.auth.core;

import java.time.Duration;
import java.util.Optional;

/**
 * 安全缓存接口 —— 定义认证模块所需的缓存操作抽象。
 * <p>
 * 用于存储登录尝试计数、账号锁定标记、JWT 会话映射、CSRF 令牌等安全相关数据。
 * 当前实现为 {@link RedisSecurityCache}，基于 Redis 提供分布式缓存能力。
 * <p>
 * 所有缓存操作均支持 TTL（生存时间），确保过期数据自动清理。
 */
public interface SecurityCache {

    /**
     * 获取缓存值。
     *
     * @param key 缓存键
     * @return 缓存值，键不存在时返回空 Optional
     */
    Optional<String> get(String key);

    /**
     * 设置缓存键值对，并指定生存时间。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   生存时间，过期后自动删除
     */
    void set(String key, String value, Duration ttl);

    /**
     * 递增计数器。
     * <p>
     * 若键不存在则初始化为 1；若键已存在则递增。
     * 首次设置时应用指定的 TTL，后续递增不重置过期时间。
     *
     * @param key 缓存键
     * @param ttl 首次创建时的生存时间
     * @return 递增后的计数值
     */
    long increment(String key, Duration ttl);

    /**
     * 删除缓存键。
     *
     * @param key 缓存键
     */
    void delete(String key);
}
