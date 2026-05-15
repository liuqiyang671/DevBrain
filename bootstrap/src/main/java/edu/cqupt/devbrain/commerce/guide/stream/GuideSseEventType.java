package edu.cqupt.devbrain.commerce.guide.stream;

import lombok.Getter;

/**
 * 导购 SSE 事件类型枚举。
 * <p>
 * 定义导购对话过程中通过 SSE 推送给前端的所有事件类型：
 * <ul>
 *   <li><b>SESSION</b> — 会话信息（sessionId、conversationId）</li>
 *   <li><b>INTENT</b> — 意图识别结果</li>
 *   <li><b>CLARIFICATION</b> — 追问问题</li>
 *   <li><b>SEARCHING</b> — 正在检索状态</li>
 *   <li><b>PRODUCT_CARD</b> — 商品卡片</li>
 *   <li><b>COMPARE_TABLE</b> — 对比表格</li>
 *   <li><b>CITATION</b> — 引用证据</li>
 *   <li><b>ANSWER_DELTA</b> — 回答增量文本</li>
 *   <li><b>ANSWER_DONE</b> — 回答完成</li>
 *   <li><b>TRACE</b> — 决策轨迹</li>
 *   <li><b>AGENT_PLAN</b> — Agent 规划动作</li>
 *   <li><b>TOOL_CALL</b> — Agent 工具调用</li>
 *   <li><b>TOOL_OBSERVATION</b> — Agent 工具执行结果</li>
 *   <li><b>AGENT_FINISH</b> — Agent 运行结束</li>
 *   <li><b>CANCEL</b> — 取消</li>
 *   <li><b>ERROR</b> — 错误信息</li>
 *   <li><b>DONE</b> — 流结束</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideSseEvent SSE 事件
 */
@Getter
public enum GuideSseEventType {

    /** 会话信息 */
    SESSION("session"),

    /** 意图识别结果 */
    INTENT("intent"),

    /** 追问问题 */
    CLARIFICATION("clarification"),

    /** 正在检索状态 */
    SEARCHING("searching"),

    /** 商品卡片 */
    PRODUCT_CARD("product_card"),

    /** 对比表格 */
    COMPARE_TABLE("compare_table"),

    /** 引用证据 */
    CITATION("citation"),

    /** 回答增量文本（流式输出） */
    ANSWER_DELTA("answer_delta"),

    /** 回答完成 */
    ANSWER_DONE("answer_done"),

    /** 决策轨迹 */
    TRACE("trace"),

    /** Agent 规划动作 */
    AGENT_PLAN("agent_plan"),

    /** Agent 工具调用 */
    TOOL_CALL("tool_call"),

    /** Agent 工具执行结果 */
    TOOL_OBSERVATION("tool_observation"),

    /** Agent 运行结束 */
    AGENT_FINISH("agent_finish"),

    /** 取消 */
    CANCEL("cancel"),

    /** 错误信息 */
    ERROR("error"),

    /** 流结束 */
    DONE("done");

    /** 事件类型的字符串值（用于 JSON 序列化） */
    private final String value;

    GuideSseEventType(String value) {
        this.value = value;
    }
}
