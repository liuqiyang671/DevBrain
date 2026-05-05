package edu.cqupt.devbrain.infra.embedding;

import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Optional;

/**
 * 嵌入模型调用目标，表示一次真实 Embedding 请求所需的完整路由信息。
 * <p>
 * {@code url} 保存最终可 POST 的 embeddings 端点，而不是 provider 基础地址。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTarget {

    private static final String DEFAULT_EMBEDDING_ENDPOINT = "/v1/embeddings";
    private static final String EMBEDDINGS_ENDPOINT_KEY = "embeddings";

    /** 提供商标识，如 siliconflow、ollama。 */
    private String provider;

    /** 提供商侧模型名称。 */
    private String model;

    /** 最终 embeddings 请求地址。 */
    private String url;

    /** API Key，本地模型可为空。 */
    private String apiKey;

    /** 向量维度，必须与模型输出和 pgvector 列定义一致。 */
    private int dimension;

    /**
     * 根据候选模型和提供商配置构建调用目标。
     *
     * @param candidate 候选模型配置
     * @param providerConfig 提供商配置
     * @return 可直接用于客户端调用的模型目标
     */
    public static ModelTarget from(
            AIModelProperties.ModelCandidate candidate,
            AIModelProperties.ProviderConfig providerConfig
    ) {
        if (candidate == null) {
            throw new RemoteException("嵌入模型候选配置不能为空");
        }
        if (isBlank(candidate.getProvider())) {
            throw new RemoteException("嵌入模型提供商不能为空");
        }
        if (providerConfig == null) {
            throw new RemoteException("嵌入模型提供商配置不存在：" + candidate.getProvider());
        }
        if (isBlank(candidate.getModel())) {
            throw new RemoteException("嵌入模型名称不能为空：" + candidate.getId());
        }
        if (candidate.getDimension() <= 0) {
            throw new RemoteException("嵌入模型向量维度必须大于 0：" + candidate.getId());
        }

        String baseUrl = firstNonBlank(candidate.getUrl(), providerConfig.getUrl());
        if (isBlank(baseUrl)) {
            throw new RemoteException("嵌入模型 API 地址不能为空：" + candidate.getProvider());
        }

        String endpoint = Optional.ofNullable(providerConfig.getEndpoints())
                .map(endpoints -> endpoints.get(EMBEDDINGS_ENDPOINT_KEY))
                .filter(ModelTarget::isNotBlank)
                .orElse(DEFAULT_EMBEDDING_ENDPOINT);

        return new ModelTarget(
                candidate.getProvider(),
                candidate.getModel(),
                resolveEmbeddingUrl(baseUrl, endpoint),
                providerConfig.getApiKey(),
                candidate.getDimension()
        );
    }

    /**
     * 兼容调用方已经传入完整 /v1/embeddings 地址的情况，避免重复拼接 endpoint。
     */
    private static String resolveEmbeddingUrl(String baseUrl, String endpoint) {
        String normalizedBase = trimTrailingSlash(baseUrl.trim());
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        if (normalizedBase.endsWith(normalizedEndpoint)) {
            return normalizedBase;
        }
        return normalizedBase + normalizedEndpoint;
    }

    private static String normalizeEndpoint(String endpoint) {
        String value = isBlank(endpoint) ? DEFAULT_EMBEDDING_ENDPOINT : endpoint.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String firstNonBlank(String first, String second) {
        return isNotBlank(first) ? first : second;
    }

    private static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
