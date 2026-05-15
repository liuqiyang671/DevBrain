package edu.cqupt.devbrain.commerce.guide.intent;

import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 槽位合并策略加载器。
 * <p>
 * 从 YAML 配置文件加载 {@link SlotMergePolicy}，默认位置：
 * classpath:prompts/guide/slot-merge-policy.yaml。
 * <p>
 * 配置项包括 minConfidence、imageOverrideMinConfidence 和 sourcePriority。
 * 未配置时使用 {@link SlotMergePolicy#defaults()} 的默认值。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see SlotMergePolicy 合并策略
 * @see SlotMergePolicyProperties 配置属性
 */
@Component
@EnableConfigurationProperties(SlotMergePolicyProperties.class)
public class SlotMergePolicyLoader {

    /** 默认策略文件位置 */
    private static final String DEFAULT_LOCATION = "prompts/guide/slot-merge-policy.yaml";

    /** 加载后的合并策略 */
    private final SlotMergePolicy policy;

    @Autowired
    public SlotMergePolicyLoader(SlotMergePolicyProperties properties) {
        this.policy = load(properties == null || properties.getLocation() == null
                ? new ClassPathResource(DEFAULT_LOCATION)
                : properties.getLocation());
    }

    /** 获取加载后的合并策略 */
        return policy;
    }

    static SlotMergePolicy load(Resource resource) {
        if (resource == null || !resource.exists()) {
            return SlotMergePolicy.defaults();
        }
        YamlMapFactoryBean factory = new YamlMapFactoryBean();
        factory.setResources(resource);
        Map<String, Object> root = factory.getObject();
        if (root == null || root.isEmpty()) {
            return SlotMergePolicy.defaults();
        }
        SlotMergePolicy defaults = SlotMergePolicy.defaults();
        return new SlotMergePolicy(
                number(root.get("minConfidence"), defaults.minConfidence()),
                number(root.get("imageOverrideMinConfidence"), defaults.imageOverrideMinConfidence()),
                sourcePriority(root.get("sourcePriority"), defaults.sourcePriority())
        );
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static List<String> sourcePriority(Object value, List<String> fallback) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return fallback;
        }
        List<String> values = collection.stream()
                .map(String::valueOf)
                .filter(org.springframework.util.StringUtils::hasText)
                .toList();
        return values.isEmpty() ? fallback : values;
    }
}
