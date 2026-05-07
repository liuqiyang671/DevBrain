package edu.cqupt.devbrain.rag.core.intent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 意图识别配置，绑定 {@code rag.intent}。
 */
@Data
@ConfigurationProperties(prefix = "rag.intent")
public class IntentProperties {

    /** 意图最低置信度。 */
    private double minScore = 0.35D;

    /** 每个子问题最多保留的意图数。 */
    private int maxCount = 3;

    /** 分数差小于该值时认为候选意图接近。 */
    private double ambiguityDelta = 0.08D;

    /** 最高分低于该值且候选接近时触发歧义引导。 */
    private double ambiguityMaxScore = 0.45D;

    /** 意图解析线程池核心线程数。 */
    private int corePoolSize = 2;

    /** 意图解析线程池最大线程数。 */
    private int maxPoolSize = 4;

    /** 意图解析线程池队列容量。 */
    private int queueCapacity = 200;
}
