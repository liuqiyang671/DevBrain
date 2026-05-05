package edu.cqupt.devbrain.framework.distributedid;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.lang.Snowflake;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 分布式 Snowflake（雪花）ID 初始化器
 * <p>
 * 在应用启动时，通过 Redis Lua 脚本获取全局唯一的 workerId（工作节点 ID）和 datacenterId（数据中心 ID），
 * 并将其注册到 Hutool 的 {@link cn.hutool.core.lang.Snowflake} 实例中，确保分布式环境下每个节点生成的 ID 不重复。
 * </p>
 * <p>
 * 该组件仅在配置项 {@code devbrain.framework.snowflake.redis-enabled=true} 时生效。
 * 如果未启用 Redis 模式，则使用 {@link CustomIdentifierGenerator} 中 Hutool 默认的雪花实例。
 * </p>
 * <p>
 * 工作流程：
 * <ol>
 *   <li>从 classpath 加载 snowflake_init.lua 脚本</li>
 *   <li>通过 Redis 执行 Lua 脚本，原子性地分配 workerId 和 datacenterId</li>
 *   <li>创建 Snowflake 实例并注册到 Hutool 全局单例中</li>
 * </ol>
 * </p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "devbrain.framework.snowflake.redis-enabled", havingValue = "true")
public class SnowflakeIdInitializer {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdInitializer.class);

    /** Redis 操作模板，用于执行 Lua 脚本获取 workerId 和 datacenterId */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 初始化 Snowflake ID 生成器
     * <p>
     * 应用启动时自动执行（{@link PostConstruct}），通过 Redis Lua 脚本获取 workerId 和 datacenterId，
     * 并创建全局唯一的 Snowflake 实例。如果初始化失败，将抛出 RuntimeException 阻止应用启动。
     * </p>
     *
     * @throws RuntimeException 如果 Redis 获取 ID 失败或初始化过程出现异常
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @PostConstruct
    public void init() {
        // 加载Lua脚本
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/snowflake_init.lua")));
        script.setResultType(List.class);

        try {
            // 执行 Lua 脚本获取 workerId 和 datacenterId
            List<Long> result = stringRedisTemplate.execute(script, Collections.emptyList());

            if (CollUtil.isEmpty(result) || result.size() != 2) {
                throw new RuntimeException("从Redis获取WorkerId和DataCenterId失败");
            }

            Long workerId = result.get(0);
            Long datacenterId = result.get(1);

            // 注册到 Hutool 的 IdUtil
            Snowflake snowflake = new Snowflake(workerId, datacenterId);
            Singleton.put(snowflake);

            log.info("分布式Snowflake初始化完成, workerId: {}, datacenterId: {}", workerId, datacenterId);
        } catch (Exception e) {
            throw new RuntimeException("分布式Snowflake初始化失败", e);
        }
    }
}
