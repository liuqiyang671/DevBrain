package edu.cqupt.devbrain.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DevBrain MCP Server 启动入口。
 * <p>
 * MCP（Model Context Protocol）服务端，为 AI 模型提供结构化的工具调用能力。
 */
@SpringBootApplication
public class DevBrainMcpServerApplication {

    /**
     * 启动 MCP Server Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DevBrainMcpServerApplication.class, args);
    }
}
