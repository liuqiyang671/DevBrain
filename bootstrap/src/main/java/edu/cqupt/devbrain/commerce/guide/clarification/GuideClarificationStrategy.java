package edu.cqupt.devbrain.commerce.guide.clarification;

/**
 * 导购追问策略接口。
 * <p>
 * 根据当前导购上下文决定是否追问、如何追问。
 * 实现类包括：
 * <ul>
 *   <li><b>PolicyGuideClarificationStrategy</b> — 基于规则的策略（快速、确定性）</li>
 *   <li><b>LLMGuideClarificationStrategy</b> — 基于 LLM 的策略（灵活、上下文感知）</li>
 *   <li><b>ValidatingGuideClarificationStrategy</b> — 装饰器，对策略结果做校验和兜底</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see ClarificationPlan 追问计划
 * @see ClarificationContext 追问上下文
 */
public interface GuideClarificationStrategy {

    /**
     * 根据上下文决定追问策略。
     *
     * @param context 追问上下文（包含意图、槽位、候选商品等信息）
     * @return 追问计划
     */
    ClarificationPlan decide(ClarificationContext context);
}
