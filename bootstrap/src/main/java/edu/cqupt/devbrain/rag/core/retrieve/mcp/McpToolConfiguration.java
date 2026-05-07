package edu.cqupt.devbrain.rag.core.retrieve.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具注册表配置。
 */
@Configuration
public class McpToolConfiguration {

    /**
     * 未配置真实 MCP 工具时，注册返回空内容的兜底实现。
     */
    @Bean
    @ConditionalOnMissingBean(McpToolRegistry.class)
    public McpToolRegistry noOpMcpToolRegistry() {
        return new NoOpMcpToolRegistry();
    }
}
