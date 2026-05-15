package edu.cqupt.devbrain.commerce.guide.intent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * 槽位合并策略配置属性。
 * <p>
 * 绑定配置前缀 {@code commerce.guide.intent.merge-policy}，定义槽位合并策略文件的位置。
 * <p>
 * 配置示例：
 * <pre>
 * commerce:
 *   guide:
 *     intent:
 *       merge-policy:
 *         location: classpath:prompts/guide/slot-merge-policy.yaml
 * </pre>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see SlotMergePolicy 槽位合并策略
 * @see SlotMergePolicyLoader 策略加载器
 */
@ConfigurationProperties(prefix = "commerce.guide.intent.merge-policy")
public class SlotMergePolicyProperties {

    /** 合合策略文件位置（支持 classpath:、file: 等 Spring Resource 前缀） */
    private Resource location;

    public Resource getLocation() {
        return location;
    }

    public void setLocation(Resource location) {
        this.location = location;
    }
}
