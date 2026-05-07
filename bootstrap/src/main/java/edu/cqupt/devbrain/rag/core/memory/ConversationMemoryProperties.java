package edu.cqupt.devbrain.rag.core.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 对话记忆配置，绑定 {@code rag.memory}。
 */
@Data
@ConfigurationProperties(prefix = "rag.memory")
public class ConversationMemoryProperties {

    /** 加载最近 N 条历史消息。 */
    private int historyKeepTurns = 20;

    /** 用户消息数超过该阈值后触发摘要压缩。 */
    private long summaryStartTurns = 10;

    /** 单次摘要最多压缩的新增消息数。 */
    private long summaryBatchSize = 100;

    /** 获取摘要分布式锁的最长等待时间。 */
    private long lockWaitMillis = 500;

    /** 摘要分布式锁租约时间。 */
    private long lockLeaseMillis = 300_000;

    /** 历史加载线程池核心线程数。 */
    private int loadCorePoolSize = 2;

    /** 历史加载线程池最大线程数。 */
    private int loadMaxPoolSize = 4;

    /** 历史加载线程池队列容量。 */
    private int loadQueueCapacity = 200;

    /** 摘要线程池核心线程数。 */
    private int summaryCorePoolSize = 1;

    /** 摘要线程池最大线程数。 */
    private int summaryMaxPoolSize = 2;

    /** 摘要线程池队列容量。 */
    private int summaryQueueCapacity = 200;
}
