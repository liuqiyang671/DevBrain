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

    /** 联网搜索配置。 */
    private WebSearchProperties webSearch = new WebSearchProperties();

    @Data
    public static class WebSearchProperties {

        /** 是否启用联网搜索能力。 */
        private boolean enabled = true;

        /** 是否对实时类问题自动联网搜索。 */
        private boolean autoTrigger = true;

        /** 搜索提供商，目前支持 duckduckgo。 */
        private String provider = "duckduckgo";

        /** DuckDuckGo HTML 搜索入口。 */
        private String endpoint = "https://duckduckgo.com/html/";

        /** 天气查询入口，用于天气类实时问题兜底。 */
        private String weatherEndpoint = "https://api.open-meteo.com/v1/forecast";

        /** 最大搜索结果数。 */
        private int maxResults = 5;

        /** 搜索请求超时时间。 */
        private int timeoutMillis = 8_000;

        /** 是否让 JVM 搜索请求使用操作系统代理设置。 */
        private boolean useSystemProxies = true;

        /** 可选显式代理地址，如 http://127.0.0.1:7892。 */
        private String proxyUrl;
    }
}
