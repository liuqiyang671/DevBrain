package edu.cqupt.devbrain.rag.core.memory;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话摘要数据对象，对应 t_conversation_summary。
 */
@Data
public class ConversationSummaryDO {

    private Long id;

    private String conversationId;

    private String userId;

    private String summary;

    private Integer messageCount;

    private Long lastSummarizedMessageId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
