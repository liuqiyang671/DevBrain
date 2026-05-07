package edu.cqupt.devbrain.rag.core.memory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 对话记忆线程池配置。
 */
@Configuration
@EnableConfigurationProperties(ConversationMemoryProperties.class)
public class ConversationMemoryConfiguration {

    /**
     * 对话记忆加载线程池，用于并行加载历史消息和摘要。
     */
    @Bean("memoryLoadExecutor")
    public Executor memoryLoadExecutor(ConversationMemoryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("memory-load-");
        executor.setCorePoolSize(properties.getLoadCorePoolSize());
        executor.setMaxPoolSize(properties.getLoadMaxPoolSize());
        executor.setQueueCapacity(properties.getLoadQueueCapacity());
        executor.initialize();
        return executor;
    }

    /**
     * 对话摘要压缩线程池，用于异步触发摘要生成。
     */
    @Bean("memorySummaryExecutor")
    public Executor memorySummaryExecutor(ConversationMemoryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("memory-summary-");
        executor.setCorePoolSize(properties.getSummaryCorePoolSize());
        executor.setMaxPoolSize(properties.getSummaryMaxPoolSize());
        executor.setQueueCapacity(properties.getSummaryQueueCapacity());
        executor.initialize();
        return executor;
    }
}
