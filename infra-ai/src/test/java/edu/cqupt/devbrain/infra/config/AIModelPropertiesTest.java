package edu.cqupt.devbrain.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AIModelPropertiesTest {

    @Test
    void bindAiModelPropertiesFromYamlStyleConfiguration() throws IOException {
        StandardEnvironment environment = environmentFromYaml("""
                ai:
                  providers:
                    siliconflow:
                      url: https://api.siliconflow.cn
                      api-key: sk-test
                      endpoints:
                        embeddings: /v1/embeddings
                    ollama:
                      url: http://localhost:11434
                  embedding:
                    default-model: qwen-emb-8b
                    candidates:
                      - id: qwen-emb-8b
                        provider: siliconflow
                        model: Qwen/Qwen3-Embedding-8B
                        dimension: 1536
                        priority: 1
                        enabled: true
                      - id: qwen-emb-local
                        provider: ollama
                        model: qwen3-embedding:8b-fp16
                        url: http://localhost:11435
                        dimension: 1536
                        priority: 2
                        enabled: false
                """);

        AIModelProperties properties = Binder.get(environment)
                .bind("ai", AIModelProperties.class)
                .get();

        assertThat(properties.getProviders()).containsKeys("siliconflow", "ollama");
        assertThat(properties.getProviders().get("siliconflow").getApiKey()).isEqualTo("sk-test");
        assertThat(properties.getProviders().get("siliconflow").getEndpoints())
                .containsEntry("embeddings", "/v1/embeddings");
        assertThat(properties.getEmbedding().getDefaultModel()).isEqualTo("qwen-emb-8b");
        assertThat(properties.getEmbedding().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getId)
                .containsExactly("qwen-emb-8b", "qwen-emb-local");
        assertThat(properties.getEmbedding().getCandidates().get(1).getUrl()).isEqualTo("http://localhost:11435");
        assertThat(properties.getEmbedding().getCandidates().get(1).isEnabled()).isFalse();
    }

    private StandardEnvironment environmentFromYaml(String yaml) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
                "test-yaml",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))
        );
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addFirst(source);
        }
        return environment;
    }
}
