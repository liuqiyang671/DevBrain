package edu.cqupt.devbrain.commerce.guide.agent.tool;

import java.util.List;
import java.util.Map;

/**
 * 导购 Agent 工具定义。
 * <p>
 * 面向 LLM Planner 暴露工具的元数据和约束，用于工具选择和执行校验：
 * <ul>
 *   <li><b>name / version / description</b> — 工具标识和描述，供 Planner 理解工具用途</li>
 *   <li><b>inputSchema</b> — JSON Schema 格式的参数定义，用于参数校验</li>
 *   <li><b>preconditions</b> — 前置条件列表（如 HAS_CANDIDATES），执行前自动检查</li>
 *   <li><b>timeoutMillis</b> — 工具执行超时时间</li>
 *   <li><b>permissionCode</b> — 所需权限码（如 commerce:guide:chat）</li>
 *   <li><b>terminal</b> — 是否为终止工具（调用后 Agent 循环结束）</li>
 * </ul>
 *
 * @param name           工具名称（唯一标识）
 * @param version        工具版本（默认 "v1"）
 * @param description    工具描述
 * @param inputSchema    JSON Schema 格式的参数定义
 * @param preconditions  前置条件列表（如 HAS_CANDIDATES、HAS_EVIDENCE）
 * @param timeoutMillis  执行超时时间（毫秒）
 * @param permissionCode 所需权限码
 * @param terminal       是否为终止工具
 * @author liuqiyang
 * @since 2026-05-15
 */
public record GuideAgentToolDefinition(
        String name,
        String version,
        String description,
        Map<String, Object> inputSchema,
        List<String> preconditions,
        long timeoutMillis,
        String permissionCode,
        boolean terminal
) {

    /** compact constructor — 防御性处理 null 字段，确保不可变集合安全 */
    public GuideAgentToolDefinition {
        version = version == null ? "v1" : version;
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
    }
}
