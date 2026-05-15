package edu.cqupt.devbrain.config;

import edu.cqupt.devbrain.infra.config.AIModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AIModelApplicationYamlTest {

    @Test
    void applicationYamlUsesSiliconflowQwen3Embedding4bByDefault() throws IOException {
        AIModelProperties properties = Binder.get(environmentFromApplicationYaml())
                .bind("ai", AIModelProperties.class)
                .get();

        assertThat(properties.getEmbedding().getDefaultModel()).isEqualTo("qwen-emb-4b");
        assertThat(properties.getEmbedding().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getId)
                .containsExactly("qwen-emb-4b", "qwen-emb-local");
        assertThat(properties.getEmbedding().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getProvider)
                .containsExactly("siliconflow", "ollama");
        assertThat(properties.getEmbedding().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getModel)
                .containsExactly("Qwen/Qwen3-Embedding-4B", "qwen3-embedding:8b-fp16");
        assertThat(properties.getEmbedding().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getDimension)
                .containsExactly(1536, 1536);
        assertThat(properties.getEmbedding().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::isEnabled)
                .containsExactly(true, false);
    }

    @Test
    void applicationYamlUsesSiliconflowAsPrimaryChatCandidate() throws IOException {
        AIModelProperties properties = Binder.get(environmentFromApplicationYaml())
                .bind("ai", AIModelProperties.class)
                .get();

        assertThat(properties.getChat().getDefaultModel()).isEqualTo("qwen-chat-siliconflow");
        assertThat(properties.getChat().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getId)
                .containsExactly(
                        "qwen-chat-siliconflow",
                        "kimi-k2-6-siliconflow",
                        "qwen3-6-35b-a3b-siliconflow",
                        "qwen-chat-local-9b",
                        "qwen-chat-local-35b"
                );
        assertThat(properties.getChat().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getProvider)
                .containsExactly("siliconflow", "siliconflow", "siliconflow", "ollama", "ollama");
        assertThat(properties.getChat().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getModel)
                .containsExactly(
                        "deepseek-ai/DeepSeek-V4-Flash",
                        "Pro/moonshotai/Kimi-K2.6",
                        "Qwen/Qwen3.6-35B-A3B",
                        "qwen3.5:9b",
                        "qwen3.6:35b-a3b"
                );
        assertThat(properties.getChat().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getPriority)
                .containsExactly(3, 1, 2, 4, 5);
    }

    private StandardEnvironment environmentFromApplicationYaml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
                "application-yaml",
                new ClassPathResource("application.yaml")
        );
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addFirst(source);
        }
        return environment;
    }
}
