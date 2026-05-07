package edu.cqupt.devbrain.rag.core.rewrite;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 查询改写配置注册。
 */
@Configuration
@EnableConfigurationProperties(QueryRewriteProperties.class)
public class QueryRewriteConfiguration {
}
