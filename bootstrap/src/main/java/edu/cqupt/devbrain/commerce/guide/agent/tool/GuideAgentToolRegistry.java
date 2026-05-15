package edu.cqupt.devbrain.commerce.guide.agent.tool;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.framework.exception.ClientException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导购 Agent 工具注册表。
 * <p>
 * 通过 Spring 自动注入所有 {@link GuideAgentTool} 实现，构建 name → tool 的映射。
 * 注册表在构造时校验工具名唯一性，运行时通过 {@link #require(String)} 按名称获取工具。
 * <p>
 * 主要职责：
 * <ul>
 *   <li><b>注册</b>：构造时自动收集所有 GuideAgentTool Bean</li>
 *   <li><b>查找</b>：按名称获取工具实例（require 方法，找不到抛异常）</li>
 *   <li><b>定义导出</b>：definitions() 导出所有工具的定义，供 Planner 使用</li>
 *   <li><b>版本管理</b>：schemaVersion() 基于工具定义计算哈希，用于 Prompt 缓存失效</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideAgentTool 工具接口
 * @see GuideAgentToolExecutor 工具执行器
 */
@Component
public class GuideAgentToolRegistry {

    /** JSON 序列化器，用于 schemaVersion 哈希计算 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 工具名称 → 工具实例的映射（保持插入顺序） */
    private final Map<String, GuideAgentTool> tools = new LinkedHashMap<>();

    /**
     * 构造注册表，自动收集并注册所有 GuideAgentTool Bean。
     * <p>
     * 构造时会校验工具名唯一性，重复名称会抛出 ClientException。
     *
     * @param tools Spring 自动注入的所有 GuideAgentTool 实现
     * @throws ClientException 工具名重复时抛出
     */
    public GuideAgentToolRegistry(List<GuideAgentTool> tools) {
        if (tools != null) {
            for (GuideAgentTool tool : tools) {
                if (tool != null && StringUtils.hasText(tool.name())) {
                    // 校验工具名唯一性，防止注册冲突
                    if (this.tools.containsKey(tool.name())) {
                        throw new ClientException("导购 Agent 工具名重复：" + tool.name());
                    }
                    this.tools.put(tool.name(), tool);
                }
            }
        }
    }

    /**
     * 按名称获取工具实例。
     *
     * @param name 工具名称
     * @return 工具实例
     * @throws ClientException 工具不存在时抛出
     */
    public GuideAgentTool require(String name) {
        GuideAgentTool tool = tools.get(name);
        if (tool == null) {
            throw new ClientException("不支持的导购 Agent 工具：" + name);
        }
        return tool;
    }

    /**
     * 获取所有已注册工具的不可变映射。
     *
     * @return 工具名称 → 工具实例的不可变映射
     */
    public Map<String, GuideAgentTool> all() {
        return Map.copyOf(tools);
    }

    /**
     * 导出所有工具的定义列表，供 LLM Planner 使用。
     *
     * @return 工具定义列表
     */
    public List<GuideAgentToolDefinition> definitions() {
        return tools.values().stream()
                .map(GuideAgentTool::definition)
                .toList();
    }

    /**
     * 计算工具 Schema 的版本哈希（前 16 位 SHA-256）。
     * <p>
     * 用于 Prompt 缓存失效检测：当工具定义变化时，哈希值也会变化，
     * 从而触发 Planner Prompt 重新生成。
     *
     * @return 16 位十六进制哈希字符串
     */
    public String schemaVersion() {
        try {
            return DigestUtil.sha256Hex(OBJECT_MAPPER.writeValueAsString(definitions())).substring(0, 16);
        } catch (JsonProcessingException ex) {
            // JSON 序列化失败时，降级为基于工具名列表的哈希
            return DigestUtil.sha256Hex(String.join(",", tools.keySet())).substring(0, 16);
        }
    }
}
