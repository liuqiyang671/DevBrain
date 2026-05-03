package edu.cqupt.devbrain.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 上传限流配置属性 —— 基于 Redisson 信号量控制上传并发数。
 * <p>
 * 配置前缀：{@code devbrain.upload.rate-limit}。
 */
@Data
@ConfigurationProperties(prefix = "devbrain.upload.rate-limit")
public class UploadRateLimitProperties {

    /** 是否启用上传限流。 */
    private boolean enabled = true;

    /** Redisson 信号量名称。 */
    private String semaphoreName = "devbrain:upload:semaphore";

    /** 信号量许可数量，即最大并发上传数。 */
    private int permits = 10;

    /** 等待许可的超时时间（毫秒），0 表示不等待立即拒绝。 */
    private long waitMillis = 0;

    /** 需要限流的上传路径列表，支持 Ant 风格匹配。 */
    private List<String> paths = List.of(
            "/knowledge-base/*/docs/upload",
            "/ingestion/tasks/upload"
    );
}
