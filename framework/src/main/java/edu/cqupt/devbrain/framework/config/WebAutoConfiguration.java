package edu.cqupt.devbrain.framework.config;

import edu.cqupt.devbrain.framework.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 层自动装配配置类。
 * <p>
 * 职责：自动注册 Web 层公共组件，包括全局异常处理器等，无需业务模块手动声明。
 */
@Configuration
public class WebAutoConfiguration {

    /**
     * 注册全局异常处理器，统一拦截并处理 Controller 层抛出的异常。
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
