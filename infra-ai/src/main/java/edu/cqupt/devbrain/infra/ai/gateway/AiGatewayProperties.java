package edu.cqupt.devbrain.infra.ai.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI网关配置属性。
 * 控制AI服务的提供商选择和各框架的启用状态。
 */
@Data
@ConfigurationProperties(prefix = "devbrain.ai.gateway")
public class AiGatewayProperties {

    private String provider = "legacy";

    private boolean springAiEnabled = false;

    private boolean langChain4jEnabled = false;
}
