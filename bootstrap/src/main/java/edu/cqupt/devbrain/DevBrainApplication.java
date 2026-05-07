package edu.cqupt.devbrain;

import edu.cqupt.devbrain.auth.core.AuthSecurityProperties;
import edu.cqupt.devbrain.infra.ai.llm.LLMService;
import edu.cqupt.devbrain.infra.ai.llm.UnavailableLLMService;
import edu.cqupt.devbrain.infra.config.AIModelProperties;
import edu.cqupt.devbrain.infra.config.RAGDefaultProperties;
import edu.cqupt.devbrain.knowledge.config.ObjectStorageProperties;
import edu.cqupt.devbrain.knowledge.config.UploadProperties;
import edu.cqupt.devbrain.knowledge.config.UploadRateLimitProperties;
import edu.cqupt.devbrain.sync.config.FeishuProperties;
import edu.cqupt.devbrain.sync.config.SyncProperties;
import edu.cqupt.devbrain.sync.config.XxlJobProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * DevBrain 应用启动类 —— Spring Boot 应用入口。
 * <p>
 * 核心注解说明：
 * <ul>
 *   <li>@SpringBootApplication — 启用 Spring Boot 自动配置和组件扫描</li>
 *   <li>@MapperScan — 扫描 MyBatis-Plus Mapper 接口所在包</li>
 *   <li>@EnableConfigurationProperties — 激活认证安全配置属性绑定</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan({
        "edu.cqupt.devbrain.user.dao.mapper",
        "edu.cqupt.devbrain.knowledge.dao.mapper",
        "edu.cqupt.devbrain.sync.dao.mapper",
        "edu.cqupt.devbrain.ingestion.dao.mapper",
        "edu.cqupt.devbrain.rag.core.intent.dao.mapper",
        "edu.cqupt.devbrain.rag.core.rewrite.dao.mapper"
})
@EnableConfigurationProperties({
        AuthSecurityProperties.class,
        UploadProperties.class,
        ObjectStorageProperties.class,
        UploadRateLimitProperties.class,
        AIModelProperties.class,
        RAGDefaultProperties.class,
        FeishuProperties.class,
        XxlJobProperties.class,
        SyncProperties.class
})
public class DevBrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevBrainApplication.class, args);
    }

    /**
     * 注册默认 LLM 兜底实现，避免没有真实聊天模型配置时阻塞应用启动。
     */
    @Bean
    @ConditionalOnMissingBean(LLMService.class)
    public LLMService unavailableLlmService() {
        return new UnavailableLLMService();
    }
}
