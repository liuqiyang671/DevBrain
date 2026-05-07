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
    void applicationYamlUsesLocalOllamaChatModelsBeforeRemoteFallback() throws IOException {
        AIModelProperties properties = Binder.get(environmentFromApplicationYaml())
                .bind("ai", AIModelProperties.class)
                .get();

        assertThat(properties.getChat().getDefaultModel()).isEqualTo("qwen-chat-local-9b");
        assertThat(properties.getChat().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getId)
                .containsExactly(
                        "qwen-chat-local-9b",
                        "qwen-chat-local-35b",
                        "qwen-chat-siliconflow"
                );
        assertThat(properties.getChat().getCandidates())
                .extracting(AIModelProperties.ModelCandidate::getProvider)
                .containsExactly("ollama", "ollama", "siliconflow");
        assertThat(properties.getChat().getCandidates().get(0).getModel())
                .isEqualTo("qwen3.5:9b");
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
