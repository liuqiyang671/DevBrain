package edu.cqupt.devbrain.infra.embedding;

import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = EmbeddingSpringContextTest.TestConfig.class,
        properties = {
                "ai.providers.ollama.url=http://localhost:11434",
                "ai.embedding.default-model=qwen-emb-local",
                "ai.embedding.candidates[0].id=qwen-emb-local",
                "ai.embedding.candidates[0].provider=ollama",
                "ai.embedding.candidates[0].model=qwen3-embedding:8b-fp16",
                "ai.embedding.candidates[0].dimension=1536",
                "ai.embedding.candidates[0].priority=1"
        }
)
class EmbeddingSpringContextTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Test
    void contextProvidesRoutingEmbeddingServiceBean() {
        assertThat(embeddingService).isInstanceOf(RoutingEmbeddingService.class);
        assertThat(embeddingClient).isInstanceOf(OllamaEmbeddingClient.class);
    }

    @Configuration
    @EnableConfigurationProperties(AIModelProperties.class)
    @Import({
            RoutingEmbeddingService.class,
            OllamaEmbeddingClient.class
    })
    static class TestConfig {

        @Bean
        OkHttpClient okHttpClient() {
            return new OkHttpClient();
        }
    }
}
