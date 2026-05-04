package edu.cqupt.devbrain.sync.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 文档同步模块自动配置，注册同步所需的公共 Bean。
 */
@Configuration
public class SyncAutoConfiguration {

    /**
     * 注册同步模块专用的 OkHttpClient Bean。
     */
    @Bean
    public OkHttpClient syncOkHttpClient(SyncProperties properties) {
        Duration timeout = properties.getHttpTimeout();
        return new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }
}
