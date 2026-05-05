package edu.cqupt.devbrain.framework.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 自动装配配置 —— 基于 Spring Redis 连接参数创建 RedissonClient。
 * <p>
 * 复用 {@code spring.data.redis} 中的 host、port、password、database 配置，
 * 避免额外维护一套 Redis 连接参数。
 */
@Configuration
@ConditionalOnClass(Redisson.class)
public class RedissonAutoConfiguration {

    /**
     * 创建 RedissonClient 实例，复用 Spring Redis 连接配置，容器关闭时自动释放连接。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        String address = String.format("redis://%s:%d",
                redisProperties.getHost(), redisProperties.getPort());

        Config config = new Config();
        config.useSingleServer().setAddress(address);
        config.useSingleServer().setDatabase(redisProperties.getDatabase());

        String password = redisProperties.getPassword();
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setPassword(password);
        }

        return Redisson.create(config);
    }
}
