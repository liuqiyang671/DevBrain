package edu.cqupt.devbrain.rag.core.retrieve.channel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 多通道检索线程池配置。
 */
@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalConfiguration {

    @Bean("retrievalExecutor")
    public Executor retrievalExecutor(RetrievalProperties properties) {
        return buildExecutor("retrieval-engine-",
                properties.getEngineCorePoolSize(),
                properties.getEngineMaxPoolSize(),
                properties.getEngineQueueCapacity());
    }

    @Bean("retrievalChannelExecutor")
    public Executor retrievalChannelExecutor(RetrievalProperties properties) {
        return buildExecutor("retrieval-channel-",
                properties.getChannelCorePoolSize(),
                properties.getChannelMaxPoolSize(),
                properties.getChannelQueueCapacity());
    }

    @Bean("retrievalCollectionExecutor")
    public Executor retrievalCollectionExecutor(RetrievalProperties properties) {
        return buildExecutor("retrieval-collection-",
                properties.getCollectionCorePoolSize(),
                properties.getCollectionMaxPoolSize(),
                properties.getCollectionQueueCapacity());
    }

    @Bean("mcpToolExecutor")
    public Executor mcpToolExecutor(RetrievalProperties properties) {
        return buildExecutor("mcp-tool-",
                properties.getMcpCorePoolSize(),
                properties.getMcpMaxPoolSize(),
                properties.getMcpQueueCapacity());
    }

    private Executor buildExecutor(String threadNamePrefix,
                                   int corePoolSize,
                                   int maxPoolSize,
                                   int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }
}
