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

class RAGDefaultPropertiesTest {

    @Test
    void defaultDimensionMatchesSiliconflowQwen3Embedding4bModel() {
        RAGDefaultProperties properties = new RAGDefaultProperties();

        assertThat(properties.getDimension()).isEqualTo(1536);
    }

    @Test
    void bindRagDefaultPropertiesFromYamlStyleConfiguration() throws IOException {
        StandardEnvironment environment = environmentFromYaml("""
                rag:
                  default:
                    collection-name: rag_default_store
                    dimension: 1536
                    metric-type: COSINE
                """);

        RAGDefaultProperties properties = Binder.get(environment)
                .bind("rag.default", RAGDefaultProperties.class)
                .get();

        assertThat(properties.getCollectionName()).isEqualTo("rag_default_store");
        assertThat(properties.getDimension()).isEqualTo(1536);
        assertThat(properties.getMetricType()).isEqualTo("COSINE");
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
