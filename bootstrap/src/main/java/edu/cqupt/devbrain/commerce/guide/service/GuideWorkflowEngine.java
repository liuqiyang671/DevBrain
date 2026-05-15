package edu.cqupt.devbrain.commerce.guide.service;

import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import edu.cqupt.devbrain.commerce.guide.observability.GuideAgentRunContext;

/**
 * 导购工作流引擎接口。
 * 驱动整个导购对话流程，从用户输入到推荐结果的完整执行。
 */
public interface GuideWorkflowEngine {

    /** 执行一轮导购工作流，返回包含推荐结果的导购状态 */
    GuideState run(GuideTurnInput input);

    /** 执行一轮带运行态观测上下文的导购工作流 */
    default GuideState run(GuideTurnInput input, GuideAgentRunContext context) {
        return run(input);
    }
}
