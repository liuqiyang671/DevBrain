package edu.cqupt.devbrain.auth.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Redis 的安全缓存实现 —— 使用 {@link StringRedisTemplate} 操作 Redis。
 * <p>
 * 实现 {@link SecurityCache} 接口，为认证模块提供分布式缓存能力，
 * 支持多实例部署场景下的会话与安全数据共享。
 * <p>
 * <b>increment 方法说明</b>：首次递增时（值为 1）设置 TTL，后续递增不重置过期时间，
 * 确保时间窗口从首次访问开始计算，避免通过持续请求无限延长窗口。
 */
@Component
public class RedisSecurityCache implements SecurityCache {

    private final StringRedisTemplate redisTemplate;

    public RedisSecurityCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * 递增计数器实现。
     * <p>
     * 使用 Redis INCR 命令原子递增。当返回值为 1 时表示键首次创建，
     * 此时设置过期时间，确保时间窗口从首次访问开始计算。
     */
    @Override
    public long increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 0 : value;
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
