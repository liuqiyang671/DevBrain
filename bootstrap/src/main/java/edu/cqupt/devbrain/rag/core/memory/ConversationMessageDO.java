package edu.cqupt.devbrain.rag.core.memory;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息数据对象，对应 t_message。
 */
@Data
public class ConversationMessageDO {

    private Long id;

    private String conversationId;

    private String userId;

    private String role;

    private String content;

    private String thinkingContent;

    private Integer thinkingDuration;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
