package edu.cqupt.devbrain.rag.enums;

import lombok.Getter;

/**
 * RAG SSE 事件类型，对应前端监听的 event name。
 */
@Getter
public enum SSEEventType {

    /** 会话元数据，包含 conversationId 和 taskId。 */
    META("meta"),

    /** 流式回答 token 增量。 */
    MESSAGE("message"),

    /** 回答完成，包含 messageId。 */
    FINISH("finish"),

    /** 流结束标记，前端收到后关闭连接。 */
    DONE("done"),

    /** 任务被用户取消。 */
    CANCEL("cancel"),

    /** 请求被拒绝（如限流、重复提交）。 */
    REJECT("reject"),

    /** 异常错误。 */
    ERROR("error");

    private final String value;

    SSEEventType(String value) {
        this.value = value;
    }
}
