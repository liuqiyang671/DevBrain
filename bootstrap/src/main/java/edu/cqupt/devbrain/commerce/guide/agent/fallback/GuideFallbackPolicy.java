package edu.cqupt.devbrain.commerce.guide.agent.fallback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本化兜底策略定义。
 * <p>
 * 以规则列表（{@link Rule}）的形式定义在不同失败场景下的恢复动作。
 * 每条规则包含：
 * <ul>
 *   <li><b>failureType</b> — 匹配的失败类型（{@link FallbackFailureType}）</li>
 *   <li><b>when</b> — 额外的事实条件（如 hasPurchaseContext、hasCandidates 等布尔谓词）</li>
 *   <li><b>actions</b> — 匹配成功后依次尝试的恢复动作列表（{@link ActionSpec}）</li>
 * </ul>
 * <p>
 * 规则匹配流程：遍历 rules → 按 failureType + when 条件过滤 →
 * 选取第一个未尝试过的 action 作为恢复计划。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideFallbackPolicyResolver 规则解析器
 * @see GuideFallbackProperties 默认策略配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideFallbackPolicy {

    /** 策略版本号，用于可观测性和灰度管理 */
    @Builder.Default
    private String version = "fallback-v1";

    /** 兜底规则列表，按顺序匹配，首个命中的规则生效 */
    @Builder.Default
    private List<Rule> rules = new ArrayList<>();

    /**
     * 单条兜底规则。
     * <p>
     * 当失败类型和 when 条件同时满足时，按 actions 顺序尝试恢复。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rule {

        /** 规则唯一标识（如 "planner-unavailable-with-intent"） */
        private String id;

        /** 匹配的失败类型，null 表示匹配所有失败类型 */
        private FallbackFailureType failureType;

        /** 额外的事实条件（如 hasPurchaseContext=true），全部满足才算命中 */
        @Builder.Default
        private Map<String, Object> when = new LinkedHashMap<>();

        /** 匹配成功后依次尝试的恢复动作列表 */
        @Builder.Default
        private List<ActionSpec> actions = new ArrayList<>();
    }

    /**
     * 恢复动作规格。
     * <p>
     * 描述一个兜底恢复动作的工具名、参数和用户可见的解释。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionSpec {

        /** 恢复动作对应的工具名（如 search_products、clarify、final_answer） */
        private String action;

        /** 工具调用参数 */
        @Builder.Default
        private Map<String, Object> arguments = new LinkedHashMap<>();

        /** 用户可见的恢复原因说明（SSE 推送给前端） */
        private String userVisibleReason;
    }
}
