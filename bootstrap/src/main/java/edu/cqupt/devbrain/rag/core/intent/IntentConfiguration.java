package edu.cqupt.devbrain.rag.core.intent;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 意图识别配置。
 */
@Configuration
@EnableConfigurationProperties(IntentProperties.class)
public class IntentConfiguration {

    /**
     * 意图解析线程池，用于子问题并行分类。
     */
    @Bean("intentExecutor")
    public Executor intentExecutor(IntentProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("intent-");
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.initialize();
        return executor;
    }
}
