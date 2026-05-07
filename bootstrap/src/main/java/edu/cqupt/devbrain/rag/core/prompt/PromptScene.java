package edu.cqupt.devbrain.rag.core.prompt;

/**
 * Prompt 场景，根据检索上下文中 MCP 和知识库的存在情况决定使用哪套模板。
 */
public enum PromptScene {

    /** 仅有知识库检索结果。 */
    KB_ONLY,

    /** 仅有 MCP 工具执行结果。 */
    MCP_ONLY,

    /** 同时有知识库和 MCP 结果。 */
    MIXED,

    /** 无任何检索结果。 */
    EMPTY
}
