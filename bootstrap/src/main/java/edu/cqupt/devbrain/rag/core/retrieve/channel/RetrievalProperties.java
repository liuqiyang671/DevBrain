package edu.cqupt.devbrain.rag.core.retrieve.channel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多通道检索配置，绑定 {@code rag.retrieval}。
 */
@Data
@ConfigurationProperties(prefix = "rag.retrieval")
public class RetrievalProperties {

    /** 顶层子问题检索线程池核心线程数。 */
    private int engineCorePoolSize = 4;

    /** 顶层子问题检索线程池最大线程数。 */
    private int engineMaxPoolSize = 8;

    /** 顶层子问题检索线程池队列容量。 */
    private int engineQueueCapacity = 500;

    /** 通道并行线程池核心线程数。 */
    private int channelCorePoolSize = 4;

    /** 通道并行线程池最大线程数。 */
    private int channelMaxPoolSize = 8;

    /** 通道并行线程池队列容量。 */
    private int channelQueueCapacity = 500;

    /** 集合并行检索线程池核心线程数。 */
    private int collectionCorePoolSize = 6;

    /** 集合并行检索线程池最大线程数。 */
    private int collectionMaxPoolSize = 12;

    /** 集合并行检索线程池队列容量。 */
    private int collectionQueueCapacity = 1000;

    /** MCP 工具调用线程池核心线程数。 */
    private int mcpCorePoolSize = 4;

    /** MCP 工具调用线程池最大线程数。 */
    private int mcpMaxPoolSize = 8;

    /** MCP 工具调用线程池队列容量。 */
    private int mcpQueueCapacity = 500;

    /** 单集合检索失败时是否忽略并返回空结果。 */
    private boolean ignoreChannelError = true;
}
