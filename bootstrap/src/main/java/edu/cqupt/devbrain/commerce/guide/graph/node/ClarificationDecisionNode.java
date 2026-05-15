package edu.cqupt.devbrain.commerce.guide.graph.node;

import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationContext;
import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlan;
import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlanMode;
import edu.cqupt.devbrain.commerce.guide.clarification.GuideClarificationProperties;
import edu.cqupt.devbrain.commerce.guide.clarification.GuideClarificationStrategy;
import edu.cqupt.devbrain.commerce.guide.clarification.PolicyGuideClarificationStrategy;
import edu.cqupt.devbrain.commerce.guide.domain.GuideClarificationState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.intent.GuideDomainOntology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 追问决策节点。
 * <p>
 * 判断当前收集的信息是否足够进行商品推荐，不足时生成追问问题。
 * 例如：缺少品类/场景信息时追问，对比场景缺少商品 ID 时追问。
 * <p>
 * 追问决策流程：
 * <ol>
 *   <li>构建追问上下文（{@link ClarificationContext}）</li>
 *   <li>调用追问策略（{@link GuideClarificationStrategy}）生成追问计划</li>
 *   <li>应用追问计划到状态：
 *     <ul>
 *       <li>跳过追问：清空追问状态，重置追问轮次</li>
 *       <li>阻断式追问：设置追问问题，等待用户回答</li>
 *       <li>非阻断追问：设置追问问题，允许同时推荐</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * 追问轮次控制：{@code clarificationTurnCount} 递增，
 * 超过阈值时策略会强制跳过追问，直接推荐。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideClarificationStrategy 追问策略接口
 * @see ClarificationPlan 追问计划
 */
@Component
public class ClarificationDecisionNode implements GuideWorkflowNode {

    private final GuideDomainOntology ontology;
    private final GuideClarificationStrategy strategy;

    public ClarificationDecisionNode() {
        this(
                GuideDomainOntology.fromResource(new ClassPathResource("prompts/guide/domain-ontology.yaml")),
                new PolicyGuideClarificationStrategy(GuideClarificationProperties.defaults())
        );
    }

    @Autowired
    public ClarificationDecisionNode(GuideDomainOntology ontology,
                                     GuideClarificationStrategy strategy) {
        this.ontology = ontology;
        this.strategy = strategy;
    }

    @Override
    public String name() {
        return "clarification_decision";
    }

    @Override
    public GuideState execute(GuideState state) {
        GuideState safeState = state == null ? new GuideState() : state;
        ClarificationPlan plan = strategy.decide(ClarificationContext.from(safeState));
        applyPlan(safeState, plan);
        return safeState;
    }

    private void applyPlan(GuideState state, ClarificationPlan plan) {
        if (state.getSlots() == null) {
            state.setSlots(new GuideSlotState());
        }
        ClarificationPlan safePlan = plan == null ? ClarificationPlan.skip("追问策略返回空计划") : normalizeQuestion(plan);
        state.setClarificationPlan(safePlan);
        List<String> missing = safePlan.targetSlots() == null ? List.of() : safePlan.targetSlots();
        state.getSlots().setMissingSlots(missing);
        if (!safePlan.shouldAsk() || safePlan.mode() == ClarificationPlanMode.SKIP) {
            state.setClarificationQuestion(null);
            state.setPendingClarification(null);
            state.setClarificationTurnCount(0);
            return;
        }
        String question = safePlan.question();
        state.setClarificationQuestion(question);
        state.setPendingClarification(GuideClarificationState.builder()
                .question(question)
                .missingSlots(missing)
                .prioritySlot(missing.isEmpty() ? null : missing.get(0))
                .askedAt(new Date())
                .answered(false)
                .mode(safePlan.mode().value())
                .reason(safePlan.reason())
                .confidence(safePlan.confidence())
                .build());
        state.setClarificationTurnCount(state.getClarificationTurnCount() + 1);
    }

    private ClarificationPlan normalizeQuestion(ClarificationPlan plan) {
        if (!plan.shouldAsk() || StringUtils.hasText(plan.question())) {
            return plan;
        }
        String question = fallbackQuestion(plan);
        return ClarificationPlan.builder()
                .shouldAsk(plan.shouldAsk())
                .mode(plan.mode())
                .question(question)
                .targetSlots(plan.targetSlots())
                .reason(plan.reason())
                .confidence(plan.confidence())
                .policyId(plan.policyId())
                .fallbackReason(plan.fallbackReason())
                .build();
    }

    private String fallbackQuestion(ClarificationPlan plan) {
        List<String> slots = plan.targetSlots();
        if (slots.contains("category")) {
            return "你主要想买什么品类？比如" + ontology.categoryExamples() + "。";
        }
        if (slots.contains("compareProducts")) {
            return "你想比较哪两款商品？可以直接发商品名或商品编号。";
        }
        if (slots.contains("scenario")) {
            return "可以补充主要用途，我会结合商品库里的价格、库存和优惠重新精排。";
        }
        return "你可以补充预算、用途、偏好品牌或是否看重优惠，我会继续帮你筛选。";
    }
}
