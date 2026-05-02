package edu.cqupt.devbrain.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证模块 Bean 配置 —— 注册认证相关的基础设施 Bean。
 * <p>
 * 当前仅配置密码编码器，后续可扩展其他认证相关 Bean。
 */
@Configuration
public class AuthBeansConfiguration {

    /**
     * 密码编码器 Bean —— 使用 BCrypt 算法进行密码哈希。
     * <p>
     * BCrypt 自带盐值，每次编码结果不同，有效防止彩虹表攻击。
     * 默认强度为 10 轮，兼顾安全性与性能。
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
