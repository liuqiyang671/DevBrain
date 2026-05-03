package edu.cqupt.devbrain.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 安全配置 —— 注册认证拦截器和跨域策略。
 * <p>
 * 配置内容：
 * <ul>
 *   <li><b>拦截器注册</b>：将 {@link AuthInterceptor} 应用于所有路径（排除 /error）</li>
 *   <li><b>CORS 跨域配置</b>：允许前端开发服务器（localhost:5173）的跨域请求</li>
 * </ul>
 * <p>
 * <b>注意</b>：当前 CORS 配置仅适用于开发环境，生产环境应替换为实际的前端域名。
 */
@Configuration
@RequiredArgsConstructor
public class WebSecurityConfiguration implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 注册认证拦截器。
     * <p>
     * 拦截所有路径（/**），排除 /error 路径（由 Spring Boot 错误处理机制管理）。
     * 公开路径的放行逻辑由 {@link AuthInterceptor} 内部处理。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/**").excludePathPatterns("/error");
    }

    /**
     * 配置 CORS 跨域映射。
     * <p>
     * 允许的来源：http://localhost:5173、http://127.0.0.1:5173（前端开发服务器）。
     * 允许的 HTTP 方法：GET、POST、PUT、PATCH、DELETE、OPTIONS。
     * 允许携带凭证（Cookie），支持基于 Cookie 的认证机制。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
