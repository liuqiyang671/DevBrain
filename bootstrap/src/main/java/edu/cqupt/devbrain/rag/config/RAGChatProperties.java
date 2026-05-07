package edu.cqupt.devbrain.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime options for RAG chat endpoints and pipeline orchestration.
 */
@Data
@ConfigurationProperties(prefix = "rag.chat")
public class RAGChatProperties {

    /**
     * SSE connection timeout in milliseconds.
     */
    private Long sseTimeoutMillis = 300_000L;

    /**
     * Default retrieval topK per sub-question.
     */
    private Integer topK = 5;

    /**
     * Max chat requests per user in one rate-limit window.
     */
    private Integer rateLimitMaxRequests = 20;

    /**
     * Chat rate-limit window in seconds.
     */
    private Long rateLimitWindowSeconds = 60L;
}
