package edu.cqupt.devbrain.sync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文档同步模块配置属性，前缀为 {@code devbrain.sync}。
 */
@Data
@ConfigurationProperties(prefix = "devbrain.sync")
public class SyncProperties {

    private int maxConcurrentSyncs = 5;
    private Duration httpTimeout = Duration.ofSeconds(30);
    private int maxContentSizeBytes = 10 * 1024 * 1024;
}
