package edu.cqupt.devbrain.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG Chat 运行时配置，绑定 {@code rag.chat}。
 */
@Data
@ConfigurationProperties(prefix = "rag.chat")
public class RAGChatProperties {

    /** SSE 连接超时时间（毫秒）。 */
    private Long sseTimeoutMillis = 300_000L;

    /** 每个子问题的默认检索条数。 */
    private Integer topK = 5;

    /** 单个限流窗口内允许的最大请求数。 */
    private Integer rateLimitMaxRequests = 20;

    /** 限流窗口时长（秒）。 */
    private Long rateLimitWindowSeconds = 60L;
}
