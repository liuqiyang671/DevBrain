package edu.cqupt.devbrain.infra.ai.gateway;

import edu.cqupt.devbrain.infra.ai.embedding.EmbeddingService;
import edu.cqupt.devbrain.infra.ai.gateway.chat.AiChatGateway;
import edu.cqupt.devbrain.infra.ai.gateway.chat.LegacyAiChatGateway;
import edu.cqupt.devbrain.infra.ai.gateway.chat.SpringAiChatGateway;
import edu.cqupt.devbrain.infra.ai.gateway.embedding.AiEmbeddingGateway;
import edu.cqupt.devbrain.infra.ai.gateway.embedding.LegacyAiEmbeddingGateway;
import edu.cqupt.devbrain.infra.ai.gateway.extract.AiStructuredExtractor;
import edu.cqupt.devbrain.infra.ai.gateway.extract.LegacyAiStructuredExtractor;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiCallObserver;
import edu.cqupt.devbrain.infra.ai.gateway.structured.AiStructuredGateway;
import edu.cqupt.devbrain.infra.ai.gateway.structured.LegacyAiStructuredGateway;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI网关自动配置类。
 * 根据 devbrain.ai.gateway.provider 配置自动装配对应的AI服务实现：
 * - legacy（默认）：使用项目原有的 LLMService 路由
 * - spring-ai：使用 Spring AI 的 ChatClient
 */
@AutoConfiguration
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiChatGateway.class)
    @ConditionalOnBean(LLMService.class)
    @ConditionalOnProperty(prefix = "devbrain.ai.gateway", name = "provider", havingValue = "legacy", matchIfMissing = true)
    public AiChatGateway legacyAiChatGateway(LLMService llmService) {
        return new LegacyAiChatGateway(llmService);
    }

    @Bean
    @ConditionalOnMissingBean(AiChatGateway.class)
    @ConditionalOnBean(ChatClient.Builder.class)
    @ConditionalOnClass(ChatClient.class)
    @ConditionalOnProperty(prefix = "devbrain.ai.gateway", name = "provider", havingValue = "spring-ai")
    public AiChatGateway springAiChatGateway(ChatClient.Builder chatClientBuilder) {
        return new SpringAiChatGateway(chatClientBuilder);
    }

    @Bean
    @ConditionalOnMissingBean(AiEmbeddingGateway.class)
    @ConditionalOnBean(EmbeddingService.class)
    public AiEmbeddingGateway legacyAiEmbeddingGateway(
            EmbeddingService embeddingService,
            AIModelProperties aiModelProperties
    ) {
        return new LegacyAiEmbeddingGateway(embeddingService, aiModelProperties);
    }

    @Bean
    @ConditionalOnMissingBean(AiStructuredExtractor.class)
    @ConditionalOnBean(AiChatGateway.class)
    public AiStructuredExtractor legacyAiStructuredExtractor(AiChatGateway chatGateway) {
        return new LegacyAiStructuredExtractor(chatGateway);
    }

    @Bean
    @ConditionalOnMissingBean(AiStructuredGateway.class)
    @ConditionalOnBean(LLMService.class)
    public AiStructuredGateway legacyAiStructuredGateway(LLMService llmService,
                                                         ObjectMapper objectMapper,
                                                         AIModelProperties aiModelProperties,
                                                         java.util.List<AiCallObserver> observers) {
        return new LegacyAiStructuredGateway(llmService, objectMapper, aiModelProperties, observers);
    }
}
