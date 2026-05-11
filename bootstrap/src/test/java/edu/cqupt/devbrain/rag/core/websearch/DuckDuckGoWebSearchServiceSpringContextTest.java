package edu.cqupt.devbrain.rag.core.websearch;

import edu.cqupt.devbrain.rag.config.RAGChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class DuckDuckGoWebSearchServiceSpringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WebSearchTestConfiguration.class)
            .withPropertyValues(
                    "rag.chat.web-search.enabled=false",
                    "rag.chat.web-search.provider=duckduckgo"
            );

    @Test
    void duckDuckGoWebSearchServiceShouldBeCreatedBySpring() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DuckDuckGoWebSearchService.class);
            assertThat(context).hasSingleBean(WebSearchService.class);
        });
    }

    @Test
    void duckDuckGoWebSearchServiceShouldEnableJvmSystemProxiesByDefault() {
        String previous = System.getProperty("java.net.useSystemProxies");
        System.clearProperty("java.net.useSystemProxies");
        try {
            contextRunner
                    .withPropertyValues("rag.chat.web-search.use-system-proxies=true")
                    .run(context -> assertThat(System.getProperty("java.net.useSystemProxies")).isEqualTo("true"));
        } finally {
            if (previous == null) {
                System.clearProperty("java.net.useSystemProxies");
            } else {
                System.setProperty("java.net.useSystemProxies", previous);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RAGChatProperties.class)
    @Import(DuckDuckGoWebSearchService.class)
    static class WebSearchTestConfiguration {
    }
}
