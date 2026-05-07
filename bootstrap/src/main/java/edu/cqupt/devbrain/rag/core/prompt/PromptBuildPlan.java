package edu.cqupt.devbrain.rag.core.prompt;

import lombok.Builder;
import lombok.Data;

/**
 * Prompt 构建计划。
 */
@Data
@Builder
public class PromptBuildPlan {

    private PromptScene scene;

    private String baseTemplate;

    private String mcpContext;

    private String kbContext;

    private String question;
}
