package edu.cqupt.devbrain.commerce.guide.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import edu.cqupt.devbrain.commerce.guide.clarification.ClarificationPlan;
import edu.cqupt.devbrain.commerce.guide.intent.GuideSlotUpdate;

import java.util.ArrayList;
import java.util.List;

/**
 * 导购工作流全局状态。
 * <p>
 * 这是贯穿整个导购对话流程的核心状态对象，在各工作流节点之间传递和累积数据。
 * 它包含了导购流程中所有的中间结果和最终结果，是导购Agent的"记忆"。
 * <p>
 * 状态组成：
 * <ul>
 *   <li><b>会话信息</b>：sessionId、userId、conversationId、agentRunId</li>
 *   <li><b>用户输入</b>：userText、imageRefs</li>
 *   <li><b>意图理解</b>：intent（购物意图）、slots（槽位状态）、slotUpdateTrace（槽位变更追踪）</li>
 *   <li><b>澄清追问</b>：clarificationQuestion、clarificationPlan、pendingClarification、clarificationTurnCount</li>
 *   <li><b>商品检索</b>：candidateProducts（候选商品）、candidateRetrievalSummary（检索摘要）</li>
 *   <li><b>证据收集</b>：evidences（推荐证据）</li>
 *   <li><b>推荐结果</b>：recommendations（推荐列表）、answerDraft（回答草稿）</li>
 *   <li><b>观测追踪</b>：decisionTrace（决策轨迹）、errors（错误信息）</li>
 * </ul>
 * <p>
 * 生命周期：
 * <pre>
 * 从GuideTurnInput创建 → 在工作流节点间传递 → 持久化到会话存储 → 下一轮恢复
 * </pre>
 *
 * @author liuqiyang
 * @since 2026-05-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideState {

    /** 会话ID，用于标识一个导购会话 */
    private String sessionId;

    /** 用户ID，用于标识用户身份 */
    private String userId;

    /** 对话ID，用于标识一个对话（可能跨多个会话） */
    private String conversationId;

    /** 当前 Agent 运行 ID，用作本轮推荐快照 ID */
    private String agentRunId;

    /** 当前轮用户输入文本 */
    private String userText;

    /** 当前轮用户上传的图片引用列表 */
    @Builder.Default
    private List<String> imageRefs = new ArrayList<>();

    /** AI识别的用户购物意图，包含意图类型、类目、预算、品牌偏好等 */
    private GuideIntent intent;

    /** 槽位状态（已收集的购物条件），包含类目、预算、场景、品牌偏好等 */
    @Builder.Default
    private GuideSlotState slots = new GuideSlotState();

    /** 槽位变更追踪，用于解释来源、置信度和标准化规则 */
    @Builder.Default
    private List<GuideSlotUpdate> slotUpdateTrace = new ArrayList<>();

    /** 追问问题（当信息不足时生成） */
    private String clarificationQuestion;

    /** 本轮结构化追问策略计划，包含追问模式、目标槽位、置信度等 */
    private ClarificationPlan clarificationPlan;

    /** 结构化追问状态，用于下一轮短回答补槽 */
    private GuideClarificationState pendingClarification;

    /** 连续追问轮次，用于避免过度追问（超过阈值时应强制推荐） */
    private int clarificationTurnCount;

    /** 候选商品列表（检索阶段产出），包含商品基本信息和检索渠道 */
    @Builder.Default
    private List<GuideCandidateProduct> candidateProducts = new ArrayList<>();

    /** 推荐证据列表，包含知识库文档片段，用于支撑推荐理由 */
    @Builder.Default
    private List<GuideEvidence> evidences = new ArrayList<>();

    /** 最终推荐结果列表，包含商品信息、推荐理由、证据等 */
    @Builder.Default
    private List<GuideRecommendation> recommendations = new ArrayList<>();

    /** 推荐回答草稿，LLM生成的最终回答文本 */
    private String answerDraft;

    /** 决策链路追踪记录，记录每一步操作的输入、输出、耗时等 */
    @Builder.Default
    private List<GuideDecisionTrace> decisionTrace = new ArrayList<>();

    /** 错误信息列表，记录执行过程中的错误 */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /** 本轮候选召回摘要，用于工作流统一 trace 和 Agent observation */
    private String candidateRetrievalSummary;

    /**
     * 从单轮输入创建初始导购状态。
     * <p>
     * 这是一个工厂方法，用于从GuideTurnInput创建一个新的GuideState对象。
     * 创建的状态只包含基本的会话信息和用户输入，其他字段使用默认值。
     *
     * @param input 导购轮次输入
     * @return 新创建的导购状态
     */
    public static GuideState from(GuideTurnInput input) {
        return GuideState.builder()
                .sessionId(input.sessionId())
                .userId(input.userId())
                .conversationId(input.conversationId())
                .agentRunId(input.agentRunId())
                .userText(input.userText())
                .imageRefs(input.imageRefs() == null ? List.of() : input.imageRefs())
                .slots(new GuideSlotState())
                .build();
    }
}
