package edu.cqupt.devbrain.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型配置属性 —— 绑定 {@code ai} 配置段，统一描述提供商和模型候选池。
 * <p>
 * 嵌入链路会按候选模型优先级选择模型；当主模型不可用时，后续服务可基于该配置自动降级到备选模型。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AIModelProperties {

    /** 提供商配置，key 为提供商名称，如 siliconflow、ollama。 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /** 嵌入模型组配置，包含默认模型和可降级的候选模型。 */
    private ModelGroup embedding = new ModelGroup();

    /** 聊天模型运行配置。 */
    private ChatProperties chat = new ChatProperties();

    /**
     * AI 提供商配置，负责保存连接入口和端点信息。
     */
    @Data
    public static class ProviderConfig {

        /** 提供商 API 基础地址，如 https://api.siliconflow.cn。 */
        private String url;

        /** 提供商 API Key；本地模型提供商可为空。 */
        private String apiKey;

        /** 端点配置，便于不同提供商覆盖 embeddings 等 API 路径。 */
        private Map<String, String> endpoints = new LinkedHashMap<>();
    }

    /**
     * 模型组配置，用于描述某一类能力的默认模型和候选模型列表。
     */
    @Data
    public static class ModelGroup {

        /** 默认模型 ID，对应 candidates 中的 id。 */
        private String defaultModel;

        /** 候选模型列表，通常按 priority 从小到大尝试。 */
        private List<ModelCandidate> candidates = List.of();
    }

    /**
     * 单个模型候选项，包含提供商、模型名称、维度和降级优先级。
     */
    @Data
    public static class ModelCandidate {

        /** 候选模型标识，供业务配置和日志引用。 */
        private String id;

        /** 提供商名称，对应 providers 的 key。 */
        private String provider;

        /** 提供商侧模型名称，如 Qwen/Qwen3-Embedding-8B。 */
        private String model;

        /** 可选覆盖地址；为空时使用 provider 级别 url。 */
        private String url;

        /** 向量维度，必须与数据库 vector(n) 列和嵌入模型输出一致。 */
        private int dimension;

        /** 优先级，数字越小越优先。 */
        private int priority;

        /** 是否启用该候选项；默认启用，便于 YAML 示例保持简洁。 */
        private boolean enabled = true;
    }

    /**
     * 聊天模型配置，包含候选模型池和超时策略。
     */
    @Data
    public static class ChatProperties {

        /** SSE message 事件分片大小，按 Unicode code point 计数。 */
        private int messageChunkSize = 256;

        /** 默认模型 ID，对应 candidates 中的 id。 */
        private String defaultModel;

        /** 候选模型列表，按 priority 从小到大尝试。 */
        private List<ModelCandidate> candidates = List.of();

        /** HTTP 连接超时（毫秒）。 */
        private int connectTimeoutMs = 30_000;

        /** HTTP 读取超时（毫秒）；SSE 长连接建议设较大值。 */
        private int readTimeoutMs = 30 * 60_000;
    }
}
