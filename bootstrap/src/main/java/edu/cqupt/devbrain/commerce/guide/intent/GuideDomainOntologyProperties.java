package edu.cqupt.devbrain.commerce.guide.intent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * 导购领域本体配置。
 * <p>
 * 绑定 {@code commerce.guide.intent.ontology} 前缀的配置项，
 * 指定领域本体 YAML 文件的位置。
 * <p>
 * 默认位置：classpath:prompts/guide/domain-ontology.yaml。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideDomainOntology 领域本体
 */
@ConfigurationProperties(prefix = "commerce.guide.intent.ontology")
public class GuideDomainOntologyProperties {

    /** 本体 YAML 文件位置（默认 classpath:prompts/guide/domain-ontology.yaml） */
    private Resource location;

    public Resource getLocation() {
        return location;
    }

    public void setLocation(Resource location) {
        this.location = location;
    }
}
