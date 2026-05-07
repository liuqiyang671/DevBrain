package edu.cqupt.devbrain.rag.core.memory;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息数据对象，对应 t_message。
 */
@Data
public class ConversationMessageDO {

    /** 消息 ID（雪花算法）。 */
    private Long id;

    /** 会话 ID。 */
    private String conversationId;

    /** 用户 ID。 */
    private String userId;

    /** 消息角色：user / assistant / system。 */
    private String role;

    /** 消息正文。 */
    private String content;

    /** 思考过程内容（仅 assistant 角色）。 */
    private String thinkingContent;

    /** 思考耗时（秒）。 */
    private Integer thinkingDuration;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
