package edu.cqupt.devbrain.rag.core.retrieve.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具注册表配置。
 */
@Configuration
public class McpToolConfiguration {

    @Bean
    @ConditionalOnMissingBean(McpToolRegistry.class)
    public McpToolRegistry noOpMcpToolRegistry() {
        return new NoOpMcpToolRegistry();
    }
}
