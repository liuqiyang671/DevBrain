package edu.cqupt.devbrain.rag.core.prompt;

import lombok.Builder;
import lombok.Data;

/**
 * Prompt 构建计划，封装场景判定结果和模板选择。
 */
@Data
@Builder
public class PromptBuildPlan {

    /** 判定的 Prompt 场景。 */
    private PromptScene scene;

    /** 选中的基础模板路径。 */
    private String baseTemplate;

    /** MCP 工具上下文文本。 */
    private String mcpContext;

    /** 知识库检索上下文文本。 */
    private String kbContext;

    /** 用户问题。 */
    private String question;
}
