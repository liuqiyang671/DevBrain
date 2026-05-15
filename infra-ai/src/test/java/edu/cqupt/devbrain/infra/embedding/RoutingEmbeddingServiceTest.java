package edu.cqupt.devbrain.infra.embedding;

import edu.cqupt.devbrain.framework.exception.RemoteException;
import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingEmbeddingServiceTest {

    @Test
    void defaultEmbedUsesConfiguredDefaultCandidate() {
        RecordingEmbeddingClient localClient = RecordingEmbeddingClient.single("ollama", List.of(0.1f, 0.2f));
        RecordingEmbeddingClient remoteClient = RecordingEmbeddingClient.single("siliconflow", List.of(0.3f, 0.4f));
        RoutingEmbeddingService service = new RoutingEmbeddingService(
                properties("qwen-emb-local", List.of(
                        candidate("qwen-emb-remote", "siliconflow", "Qwen/Qwen3-Embedding-8B", 2, 1, true),
                        candidate("qwen-emb-local", "ollama", "qwen3-embedding:8b-fp16", 2, 2, true)
                )),
                List.of(localClient, remoteClient)
        );

        List<Float> embedding = service.embed("ai-shopping-agent");

        assertThat(service).isInstanceOf(EmbeddingService.class);
        assertThat(RoutingEmbeddingService.class).hasAnnotation(Service.class);
        assertThat(embedding).containsExactly(0.1f, 0.2f);
        assertThat(localClient.lastTarget.getProvider()).isEqualTo("ollama");
        assertThat(localClient.lastTarget.getModel()).isEqualTo("qwen3-embedding:8b-fp16");
        assertThat(localClient.lastText).isEqualTo("ai-shopping-agent");
        assertThat(remoteClient.lastTarget).isNull();
    }

    @Test
    void specifiedModelUsesMatchingCandidate() {
        RecordingEmbeddingClient localClient = RecordingEmbeddingClient.single("ollama", List.of(0.1f, 0.2f));
        RecordingEmbeddingClient remoteClient = RecordingEmbeddingClient.single("siliconflow", List.of(0.3f, 0.4f));
        RoutingEmbeddingService service = new RoutingEmbeddingService(
                properties("qwen-emb-remote", List.of(
                        candidate("qwen-emb-remote", "siliconflow", "Qwen/Qwen3-Embedding-8B", 2, 1, true),
                        candidate("qwen-emb-local", "ollama", "qwen3-embedding:8b-fp16", 2, 2, true)
                )),
                List.of(localClient, remoteClient)
        );

        List<Float> embedding = service.embed("ai-shopping-agent", "qwen-emb-local");

        assertThat(embedding).containsExactly(0.1f, 0.2f);
        assertThat(localClient.lastTarget.getProvider()).isEqualTo("ollama");
        assertThat(remoteClient.lastTarget).isNull();
    }

    @Test
    void defaultEmbedFallsBackToNextEnabledCandidateWhenDefaultFails() {
        RecordingEmbeddingClient remoteClient = RecordingEmbeddingClient.failing("siliconflow");
        RecordingEmbeddingClient localClient = RecordingEmbeddingClient.single("ollama", List.of(0.1f, 0.2f));
        RoutingEmbeddingService service = new RoutingEmbeddingService(
                properties("qwen-emb-remote", List.of(
                        candidate("qwen-emb-remote", "siliconflow", "Qwen/Qwen3-Embedding-8B", 2, 1, true),
                        candidate("qwen-emb-local", "ollama", "qwen3-embedding:8b-fp16", 2, 2, true)
                )),
                List.of(remoteClient, localClient)
        );

        List<Float> embedding = service.embed("ai-shopping-agent");

        assertThat(embedding).containsExactly(0.1f, 0.2f);
        assertThat(remoteClient.lastTarget.getProvider()).isEqualTo("siliconflow");
        assertThat(localClient.lastTarget.getProvider()).isEqualTo("ollama");
    }

    @Test
    void batchEmbedDelegatesToSelectedClient() {
        RecordingEmbeddingClient localClient = RecordingEmbeddingClient.batch(
                "ollama",
                List.of(List.of(0.1f, 0.2f), List.of(0.3f, 0.4f))
        );
        RoutingEmbeddingService service = new RoutingEmbeddingService(
                properties("qwen-emb-local", List.of(
                        candidate("qwen-emb-local", "ollama", "qwen3-embedding:8b-fp16", 2, 1, true)
                )),
                List.of(localClient)
        );

        List<List<Float>> embeddings = service.embedBatch(List.of("文本1", "文本2"));

        assertThat(embeddings).containsExactly(List.of(0.1f, 0.2f), List.of(0.3f, 0.4f));
        assertThat(localClient.lastTexts).containsExactly("文本1", "文本2");
    }

    @Test
    void emptyBatchReturnsEmptyListWithoutResolvingClient() {
        RecordingEmbeddingClient localClient = RecordingEmbeddingClient.single("ollama", List.of(0.1f, 0.2f));
        RoutingEmbeddingService service = new RoutingEmbeddingService(
                properties("qwen-emb-local", List.of(
                        candidate("qwen-emb-local", "ollama", "qwen3-embedding:8b-fp16", 2, 1, true)
                )),
                List.of(localClient)
        );

        assertThat(service.embedBatch(List.of())).isEmpty();
        assertThat(localClient.lastTarget).isNull();
    }

    @Test
    void missingCandidateThrowsRemoteException() {
        RoutingEmbeddingService service = new RoutingEmbeddingService(
                properties("missing", List.of(
                        candidate("qwen-emb-local", "ollama", "qwen3-embedding:8b-fp16", 2, 1, true)
                )),
                List.of(RecordingEmbeddingClient.single("ollama", List.of(0.1f, 0.2f)))
        );

        assertThatThrownBy(() -> service.embed("ai-shopping-agent"))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("默认嵌入模型不可用");
    }

    @Test
    void missingClientThrowsRemoteException() {
        RoutingEmbeddingService service = new RoutingEmbeddingService(
                properties("qwen-emb-local", List.of(
                        candidate("qwen-emb-local", "ollama", "qwen3-embedding:8b-fp16", 2, 1, true)
                )),
                List.of()
        );

        assertThatThrownBy(() -> service.embed("ai-shopping-agent"))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("嵌入模型客户端不存在：ollama");
    }

    @Test
    void mismatchedVectorDimensionThrowsRemoteException() {
        RoutingEmbeddingService service = new RoutingEmbeddingService(
                properties("qwen-emb-local", List.of(
                        candidate("qwen-emb-local", "ollama", "qwen3-embedding:8b-fp16", 3, 1, true)
                )),
                List.of(RecordingEmbeddingClient.single("ollama", List.of(0.1f, 0.2f)))
        );

        assertThatThrownBy(() -> service.embed("ai-shopping-agent"))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("Embedding 返回维度不匹配")
                .hasMessageContaining("expected=3")
                .hasMessageContaining("actual=2");
    }

    private AIModelProperties properties(String defaultModel, List<AIModelProperties.ModelCandidate> candidates) {
        AIModelProperties properties = new AIModelProperties();
        Map<String, AIModelProperties.ProviderConfig> providers = new LinkedHashMap<>();
        providers.put("ollama", provider("http://localhost:11434", null));
        providers.put("siliconflow", provider("https://api.siliconflow.cn", "sk-test"));
        properties.setProviders(providers);
        properties.getEmbedding().setDefaultModel(defaultModel);
        properties.getEmbedding().setCandidates(candidates);
        return properties;
    }

    private AIModelProperties.ProviderConfig provider(String url, String apiKey) {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(url);
        provider.setApiKey(apiKey);
        return provider;
    }

    private AIModelProperties.ModelCandidate candidate(
            String id,
            String provider,
            String model,
            int dimension,
            int priority,
            boolean enabled
    ) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setDimension(dimension);
        candidate.setPriority(priority);
        candidate.setEnabled(enabled);
        return candidate;
    }

    private static final class RecordingEmbeddingClient implements EmbeddingClient {

        private final String provider;
        private final List<List<Float>> batchEmbeddings;
        private final boolean fail;
        private ModelTarget lastTarget;
        private String lastText;
        private List<String> lastTexts;

        private static RecordingEmbeddingClient single(String provider, List<Float> embedding) {
            return new RecordingEmbeddingClient(provider, List.of(embedding));
        }

        private static RecordingEmbeddingClient batch(String provider, List<List<Float>> batchEmbeddings) {
            return new RecordingEmbeddingClient(provider, batchEmbeddings);
        }

        private static RecordingEmbeddingClient failing(String provider) {
            return new RecordingEmbeddingClient(provider, List.of(), true);
        }

        private RecordingEmbeddingClient(String provider, List<List<Float>> batchEmbeddings) {
            this(provider, batchEmbeddings, false);
        }

        private RecordingEmbeddingClient(String provider, List<List<Float>> batchEmbeddings, boolean fail) {
            this.provider = provider;
            this.batchEmbeddings = batchEmbeddings;
            this.fail = fail;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public List<Float> embed(String text, ModelTarget target) {
            this.lastText = text;
            this.lastTarget = target;
            if (fail) {
                throw new RemoteException("mock failure: " + provider);
            }
            return batchEmbeddings.get(0);
        }

        @Override
        public List<List<Float>> embedBatch(List<String> texts, ModelTarget target) {
            this.lastTexts = texts;
            this.lastTarget = target;
            if (fail) {
                throw new RemoteException("mock failure: " + provider);
            }
            return batchEmbeddings;
        }
    }
}
