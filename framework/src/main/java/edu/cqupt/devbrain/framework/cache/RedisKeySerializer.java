package edu.cqupt.devbrain.framework.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Key 序列化器，在写入 Redis 时自动为 key 添加统一前缀。
 * <p>
 * 职责：
 * <ul>
 *     <li>读取配置项 {@code framework.cache.redis.prefix} 作为 key 前缀</li>
 *     <li>序列化时将前缀与原始 key 拼接后写入 Redis</li>
 *     <li>反序列化时原样返回字节内容（不做前缀剥离）</li>
 * </ul>
 * <p>
 * 仅当配置了 {@code framework.cache.redis.prefix} 时才会生效（{@code @ConditionalOnProperty}）。
 */
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "framework.cache.redis.prefix")
public class RedisKeySerializer implements RedisSerializer<String> {

    @Value("${framework.cache.redis.prefix:}")
    private String keyPrefix;

    /**
     * 序列化 Redis key，将配置的前缀与原始 key 拼接后转为字节数组。
     */
    @Override
    public byte[] serialize(String key) throws SerializationException {
        String builderKey = keyPrefix + key;
        return builderKey.getBytes();
    }

    /**
     * 反序列化 Redis key，将字节数组转为 UTF-8 字符串。
     */
    @Override
    public String deserialize(byte[] bytes) throws SerializationException {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
