package edu.cqupt.devbrain.infra.embedding;

import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

/**
 * SiliconFlow Embedding 客户端。
 * <p>
 * SiliconFlow 兼容 OpenAI embeddings 协议，远程调用需要 API Key，单批最大 32 条文本。
 */
@Component
public class SiliconFlowEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    /**
     * 注入共享 OkHttpClient，复用连接池和超时配置。
     */
    public SiliconFlowEmbeddingClient(OkHttpClient httpClient) {
        super(httpClient);
    }

    /**
     * 返回提供商标识，用于按配置中的 provider 路由客户端。
     */
    @Override
    public String provider() {
        return "siliconflow";
    }

    /**
     * Qwen3 Embedding 支持通过 dimensions 指定输出维度，保持返回向量与 pgvector 列一致。
     */
    @Override
    protected void customizeRequestBody(JsonObject body, ModelTarget target) {
        super.customizeRequestBody(body, target);
        body.addProperty("dimensions", target.getDimension());
    }

    /**
     * SiliconFlow embeddings 接口单批最多处理 32 条文本。
     */
    @Override
    protected int maxBatchSize() {
        return 32;
    }
}
