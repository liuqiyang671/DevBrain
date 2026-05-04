package edu.cqupt.devbrain.sync.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * XXL-Job 执行器自动配置，仅在 {@code devbrain.sync.xxl-job.enabled=true} 时生效。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "devbrain.sync.xxl-job", name = "enabled", havingValue = "true")
public class XxlJobAutoConfiguration {

    /**
     * 注册 XXL-Job 执行器 Bean。
     */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        if (!StringUtils.hasText(properties.getAdminAddresses())) {
            throw new IllegalStateException("XXL_JOB_ADMIN_ADDRESSES must be configured when XXL_JOB_ENABLED=true");
        }
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAccessToken(properties.getAccessToken());
        executor.setAppname(properties.getAppname());
        executor.setPort(properties.getPort());
        executor.setLogPath(properties.getLogPath());
        executor.setLogRetentionDays(properties.getLogRetentionDays());
        log.info("XXL-Job executor initialized: appname={}, admin={}", properties.getAppname(), properties.getAdminAddresses());
        return executor;
    }
}
