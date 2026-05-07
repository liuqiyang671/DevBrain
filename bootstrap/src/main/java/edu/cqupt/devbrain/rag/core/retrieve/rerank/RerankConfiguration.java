package edu.cqupt.devbrain.rag.core.retrieve.rerank;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rerank 服务配置。
 */
@Configuration
public class RerankConfiguration {

    /**
     * 未配置真实 Rerank 模型时，注册按现有分数排序的兜底实现。
     */
    @Bean
    @ConditionalOnMissingBean(RerankService.class)
    public RerankService noOpRerankService() {
        return new NoOpRerankService();
    }
}
