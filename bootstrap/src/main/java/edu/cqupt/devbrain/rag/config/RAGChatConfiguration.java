package edu.cqupt.devbrain.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * RAG Chat 流水线配置，注册流式任务管理线程池。
 */
@Configuration
@EnableConfigurationProperties(RAGChatProperties.class)
public class RAGChatConfiguration {

    /**
     * 流式任务管理线程池，用于处理取消信号的广播订阅回调。
     */
    @Bean("streamTaskExecutor")
    public Executor streamTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rag-stream-task-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }
}
