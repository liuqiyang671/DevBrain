package edu.cqupt.devbrain.sync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XXL-Job 执行器配置属性，前缀为 {@code devbrain.sync.xxl-job}。
 */
@Data
@ConfigurationProperties(prefix = "devbrain.sync.xxl-job")
public class XxlJobProperties {

    private boolean enabled = false;
    private String adminAddresses;
    private String accessToken = "devbrain-xxl-job-token";
    private String appname = "devbrain-executor";
    private int port = 9999;
    private String logPath = "./logs/xxl-job";
    private int logRetentionDays = 30;
}
