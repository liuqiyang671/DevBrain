package edu.cqupt.devbrain.sync.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class SyncAutoConfiguration {

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
