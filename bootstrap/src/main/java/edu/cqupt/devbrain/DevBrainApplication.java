package edu.cqupt.devbrain;

import edu.cqupt.devbrain.auth.core.AuthSecurityProperties;
import edu.cqupt.devbrain.knowledge.config.ObjectStorageProperties;
import edu.cqupt.devbrain.knowledge.config.UploadProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

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
        "edu.cqupt.devbrain.knowledge.dao.mapper"
})
@EnableConfigurationProperties({AuthSecurityProperties.class, UploadProperties.class, ObjectStorageProperties.class})
public class DevBrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevBrainApplication.class, args);
    }
}
