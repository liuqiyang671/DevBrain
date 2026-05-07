package edu.cqupt.devbrain.rag.core.retrieve.rerank;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rerank 服务配置。
 */
@Configuration
public class RerankConfiguration {

    @Bean
    @ConditionalOnMissingBean(RerankService.class)
    public RerankService noOpRerankService() {
        return new NoOpRerankService();
    }
}
