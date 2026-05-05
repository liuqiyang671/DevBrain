package edu.cqupt.devbrain.infra.embedding;

import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

/**
 * Ollama 本地 Embedding 客户端。
 * <p>
 * Ollama 兼容 OpenAI embeddings 协议，但本地服务通常不需要 API Key，也不需要 encoding_format 参数。
 */
@Component
public class OllamaEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    /**
     * 注入共享 OkHttpClient，复用连接池和超时配置。
     */
    public OllamaEmbeddingClient(OkHttpClient httpClient) {
        super(httpClient);
    }

    /**
     * 返回提供商标识，用于按配置中的 provider 路由客户端。
     */
    @Override
    public String provider() {
        return "ollama";
    }

    /**
     * Ollama 本地接口不要求 Authorization 认证头。
     */
    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    /**
     * Ollama 不需要 OpenAI 的 encoding_format 扩展参数，保持请求体最小化。
     */
    @Override
    protected void customizeRequestBody(JsonObject body, ModelTarget target) {
        body.addProperty("dimensions", target.getDimension());
    }

    /**
     * Ollama 本地服务不在客户端侧限制批量大小。
     */
    @Override
    protected int maxBatchSize() {
        return 0;
    }
}
