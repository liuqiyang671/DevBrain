package edu.cqupt.devbrain.infra.embedding;

import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelTargetTest {

    @Test
    void buildTargetWithProviderEmbeddingEndpoint() {
        AIModelProperties.ModelCandidate candidate = candidate("siliconflow", "Qwen/Qwen3-Embedding-8B", null, 1536);
        AIModelProperties.ProviderConfig provider = provider(
                "https://api.siliconflow.cn/",
                "sk-test",
                Map.of("embeddings", "/v1/embeddings")
        );

        ModelTarget target = ModelTarget.from(candidate, provider);

        assertThat(target.getProvider()).isEqualTo("siliconflow");
        assertThat(target.getModel()).isEqualTo("Qwen/Qwen3-Embedding-8B");
        assertThat(target.getUrl()).isEqualTo("https://api.siliconflow.cn/v1/embeddings");
        assertThat(target.getApiKey()).isEqualTo("sk-test");
        assertThat(target.getDimension()).isEqualTo(1536);
    }

    @Test
    void candidateUrlOverridesProviderUrl() {
        AIModelProperties.ModelCandidate candidate = candidate(
                "ollama",
                "qwen3-embedding:8b-fp16",
                "http://localhost:11435",
                1536
        );
        AIModelProperties.ProviderConfig provider = provider("http://localhost:11434", null, Map.of());

        ModelTarget target = ModelTarget.from(candidate, provider);

        assertThat(target.getUrl()).isEqualTo("http://localhost:11435/v1/embeddings");
    }

    @Test
    void defaultEndpointIsUsedWhenProviderEndpointMissing() {
        AIModelProperties.ModelCandidate candidate = candidate("ollama", "qwen3-embedding:8b-fp16", null, 1536);
        AIModelProperties.ProviderConfig provider = provider("http://localhost:11434/api", null, Map.of());

        ModelTarget target = ModelTarget.from(candidate, provider);

        assertThat(target.getUrl()).isEqualTo("http://localhost:11434/api/v1/embeddings");
    }

    @Test
    void fullEmbeddingUrlIsKeptWhenCandidateAlreadyContainsEndpoint() {
        AIModelProperties.ModelCandidate candidate = candidate(
                "custom",
                "custom-embedding",
                "https://example.com/v1/embeddings",
                1536
        );
        AIModelProperties.ProviderConfig provider = provider("https://ignored.example.com", "sk", Map.of());

        ModelTarget target = ModelTarget.from(candidate, provider);

        assertThat(target.getUrl()).isEqualTo("https://example.com/v1/embeddings");
    }

    @Test
    void missingProviderConfigThrowsRemoteException() {
        AIModelProperties.ModelCandidate candidate = candidate("missing", "model", null, 1536);

        assertThatThrownBy(() -> ModelTarget.from(candidate, null))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("提供商配置不存在");
    }

    @Test
    void missingProviderUrlThrowsRemoteException() {
        AIModelProperties.ModelCandidate candidate = candidate("siliconflow", "model", null, 1536);
        AIModelProperties.ProviderConfig provider = provider(null, "sk", Map.of());

        assertThatThrownBy(() -> ModelTarget.from(candidate, provider))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("API 地址不能为空");
    }

    @Test
    void missingModelThrowsRemoteException() {
        AIModelProperties.ModelCandidate candidate = candidate("siliconflow", " ", null, 1536);
        AIModelProperties.ProviderConfig provider = provider("https://api.siliconflow.cn", "sk", Map.of());

        assertThatThrownBy(() -> ModelTarget.from(candidate, provider))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("模型名称不能为空");
    }

    @Test
    void invalidDimensionThrowsRemoteException() {
        AIModelProperties.ModelCandidate candidate = candidate("siliconflow", "model", null, 0);
        AIModelProperties.ProviderConfig provider = provider("https://api.siliconflow.cn", "sk", Map.of());

        assertThatThrownBy(() -> ModelTarget.from(candidate, provider))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("向量维度必须大于 0");
    }

    private AIModelProperties.ModelCandidate candidate(String provider, String model, String url, int dimension) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("candidate-id");
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setUrl(url);
        candidate.setDimension(dimension);
        candidate.setPriority(1);
        return candidate;
    }

    private AIModelProperties.ProviderConfig provider(String url, String apiKey, Map<String, String> endpoints) {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(url);
        provider.setApiKey(apiKey);
        provider.setEndpoints(endpoints);
        return provider;
    }
}
