package edu.cqupt.devbrain.rag.core.memory;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话摘要数据对象，对应 t_conversation_summary。
 */
@Data
public class ConversationSummaryDO {

    /** 摘要记录 ID。 */
    private Long id;

    /** 会话 ID。 */
    private String conversationId;

    /** 用户 ID。 */
    private String userId;

    /** 摘要文本。 */
    private String summary;

    /** 已压缩的消息总数。 */
    private Integer messageCount;

    /** 最后一条被压缩消息的 ID，用于增量压缩。 */
    private Long lastSummarizedMessageId;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
