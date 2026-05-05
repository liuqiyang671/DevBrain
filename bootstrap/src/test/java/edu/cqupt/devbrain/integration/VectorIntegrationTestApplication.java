package edu.cqupt.devbrain.integration;

import edu.cqupt.devbrain.infra.config.RAGDefaultProperties;
import edu.cqupt.devbrain.rag.core.retrieve.PgRetrieverService;
import edu.cqupt.devbrain.rag.core.vector.PgVectorStoreAdmin;
import edu.cqupt.devbrain.rag.core.vector.PgVectorStoreService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 向量链路集成测试专用 Spring Boot 配置。
 * <p>
 * 只装配 Embedding、PgVector 写入和检索所需 Bean，避免完整应用上下文拉起
 * 认证、对象存储、任务调度等与本测试无关的外部依赖。
 */
@TestConfiguration
@EnableAutoConfiguration
@EnableConfigurationProperties(RAGDefaultProperties.class)
@Import({
        PgVectorStoreAdmin.class,
        PgVectorStoreService.class,
        PgRetrieverService.class,
        VectorIntegrationTestConfig.class
})
public class VectorIntegrationTestApplication {
}
